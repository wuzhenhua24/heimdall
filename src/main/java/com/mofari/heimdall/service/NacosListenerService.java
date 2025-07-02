package com.mofari.heimdall.service;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.mofari.heimdall.websocket.AppStatusWebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class NacosListenerService {
    private static final Logger logger = LoggerFactory.getLogger(NacosListenerService.class);

    @Autowired
    private NamingService namingService; // Nacos 客户端核心 API

    @Autowired
    private AppStatusStore appStatusStore; // ✅ 注入状态存储服务

    @Autowired
    private DingTalkNotifierService dingTalkNotifierService;

    @Autowired
    private AppInfoService appInfoService;

    // 假设我们只关心 "DEFAULT_GROUP" 分组下的服务
    private static final String SERVICE_GROUP = "DEFAULT_GROUP";

    @PostConstruct
    public void init() throws Exception {
        // 获取 Nacos 中所有服务的名称
        List<String> serviceNames = namingService.getServicesOfServer(1, Integer.MAX_VALUE, SERVICE_GROUP).getData();
        logger.info("Found {} services in total from Nacos.", serviceNames.size());

        // ✅ 核心变更：使用 Stream API 过滤掉服务名中包含 "sidecar" 的服务
        // 为了更健壮，我们忽略大小写进行匹配
        List<String> targetServiceNames = serviceNames.stream()
                .filter(serviceName -> !serviceName.toLowerCase().contains("sidecar"))
                .collect(Collectors.toList());
        logger.info("After filtering, {} services will be monitored: {}", targetServiceNames.size(), targetServiceNames);

        for (String serviceName : targetServiceNames) {
            // 为每个服务注册一个监听器
            namingService.subscribe(serviceName, SERVICE_GROUP, event -> {
                if (event instanceof com.alibaba.nacos.api.naming.listener.NamingEvent) {
                    com.alibaba.nacos.api.naming.listener.NamingEvent namingEvent = (com.alibaba.nacos.api.naming.listener.NamingEvent) event;
                    handleNacosEvent(namingEvent);
                }
            });
        }
        logger.info("Nacos listeners initialized for " + serviceNames.size() + " services.");
    }

    /**
     * 统一处理 Nacos 事件
     * @param namingEvent Nacos 推送的事件
     */
    private void handleNacosEvent(NamingEvent namingEvent) {
        String serviceId = namingEvent.getServiceName();
        String displayName = serviceId.endsWith(".app") ? serviceId.substring(0, serviceId.length() - 4) : serviceId;

        final Set<String> targetClusters = Set.of("daily-default", "your-main-cluster-name"); // 你的目标集群

        List<Instance> filteredInstances = namingEvent.getInstances().stream()
                .filter(instance -> targetClusters.contains(instance.getClusterName()))
                .collect(Collectors.toList());

        // 计算新状态
        String newStatus = calculateStatus(filteredInstances);

        // ✅ 核心告警逻辑
        // 1. 从状态存储中获取旧的状态
        Map<String, Object> oldStatusMap = appStatusStore.getStatus(serviceId);
        String oldStatus = (oldStatusMap != null && oldStatusMap.get("status") != null)
                ? oldStatusMap.get("status").toString()
                : "UNKNOWN"; // 如果是第一次看到这个服务，旧状态设为 UNKNOWN

        // 2. 只有当状态发生变化时，才进行处理
        if (!newStatus.equals(oldStatus)) {
            logger.info("状态变更: 服务 '{}' 从 '{}' 变为 '{}'", serviceId, oldStatus, newStatus);

            // 3. 判断是否需要发送告警
            // ✅ 核心修改：增加对 oldStatus 的判断，确保不是从 UNKNOWN 状态变为 DOWN
            if ("DOWN".equals(newStatus) && !"UNKNOWN".equals(oldStatus)) {

                // ✅ 在发送告警前，获取负责人信息
                List<String> owners = appInfoService.getAppOwners(displayName);
                String ownerText = owners.isEmpty() ? "未指定" : String.join(", ", owners);
                // 发送宕机告警
                String title = "🚨 服务宕机警报";
                String text = String.format("#### %s\n\n> **服务名**: %s\n\n> **负责人**: %s\n\n> **当前状态**: <font color='#dd0000'>**%s**</font>\n\n> **时间**: %s",
                        title, serviceId, ownerText, newStatus, getCurrentTimestamp());
                dingTalkNotifierService.sendMarkdownMessage(title, text);

            } else if ("RUNNING".equals(newStatus) && "DOWN".equals(oldStatus)) {
                // 如果是从 DOWN 恢复到 RUNNING，发送恢复通知
                String title = "✅ 服务恢复通知";
                String text = String.format("#### %s\n\n> **服务名**: %s\n\n> **当前状态**: <font color='#008000'>**%s**</font>\n\n> **时间**: %s",
                        title, serviceId, newStatus, getCurrentTimestamp());
                dingTalkNotifierService.sendMarkdownMessage(title, text);
            }
        }


        // 创建消息体并更新
        Map<String, Object> message = new HashMap<>();
        message.put("id", serviceId);
        message.put("name", displayName);

        // 只有当状态变化时才更新状态和广播，避免无效更新
        if (!newStatus.equals(oldStatus)) {
            message.put("status", newStatus);
            appStatusStore.updateStatus(message);
            AppStatusWebSocketServer.broadcast(message);
        }
    }


    /**
     * 根据实例列表计算服务的总体状态
     */
    private String calculateStatus(List<Instance> instances) {
        if (instances.isEmpty()) {
            return "OFFLINE";
        }
        long totalInstances = instances.size();
        long healthyInstances = instances.stream().filter(Instance::isHealthy).count();
        if (healthyInstances == 0) {
            return "DOWN";
        } else if (healthyInstances < totalInstances) {
            return "DEGRADED";
        } else {
            return "RUNNING";
        }
    }

    private String getCurrentTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }
}
