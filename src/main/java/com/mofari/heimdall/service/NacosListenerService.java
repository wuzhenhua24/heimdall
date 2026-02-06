package com.mofari.heimdall.service;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.mofari.heimdall.config.MonitoringProperties;
import com.mofari.heimdall.websocket.AppStatusWebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicInteger; // ✅ 更新点：引入原子计数器


@Service
public class NacosListenerService {
    private static final Logger logger = LoggerFactory.getLogger(NacosListenerService.class);

    @Autowired
    private NamingService namingService; // Nacos 客户端核心 API

    @Autowired
    private AppStatusStore appStatusStore; // ✅ 注入状态存储服务

    @Autowired
    private AlertNotifier alertNotifier;

    @Autowired
    private AppInfoService appInfoService;

    @Autowired
    private MonitoringProperties monitoringProperties;

    // 假设我们只关心 "DEFAULT_GROUP" 分组下的服务
    private static final String SERVICE_GROUP = "DEFAULT_GROUP";

    private final ConcurrentHashMap<String, Object> serviceLocks = new ConcurrentHashMap<>();

    private Set<String> whitelistSet;
    private Set<String> targetClusterSet;
    private List<String> excludeKeywords;

    // ✅ 更新点：新增全局状态追踪变量
    private int totalMonitoredServices = 0; // 监控的服务总数
    private final AtomicInteger downServiceCount = new AtomicInteger(0); // 当前 DOWN 的服务数量
    private volatile boolean isGlobalAlertSent = false; // 全局告警是否已发送的标记

    @PostConstruct
    public void init() throws Exception {
        this.whitelistSet = new HashSet<>(monitoringProperties.getWhitelistServices());
        this.targetClusterSet = new HashSet<>(monitoringProperties.getTargetClusters());
        this.excludeKeywords = monitoringProperties.getExcludeServiceKeywords();
        logger.info("Monitoring properties loaded: targetClusters={}, whitelist={}, excludeKeywords={}",
                this.targetClusterSet, this.whitelistSet, this.excludeKeywords);


        // 获取 Nacos 中所有服务的名称
        List<String> serviceNames = namingService.getServicesOfServer(1, Integer.MAX_VALUE, SERVICE_GROUP).getData();
        logger.info("Found {} services in total from Nacos.", serviceNames.size());

        // 使用配置的关键词和白名单进行过滤
        List<String> targetServiceNames = serviceNames.stream()
                .filter(serviceName -> excludeKeywords.stream()
                        .noneMatch(keyword -> serviceName.toLowerCase().contains(keyword.toLowerCase())))
                .filter(serviceName -> !whitelistSet.contains(serviceName))
                .collect(Collectors.toList());

        logger.info("After filtering, {} services will be monitored: {}", targetServiceNames.size(), targetServiceNames);
        // ✅ 更新点：记录监控的服务总数
        this.totalMonitoredServices = targetServiceNames.size();
        logger.info("Total services to be monitored: {}", this.totalMonitoredServices);

        // ✅ 更新点：在订阅前，初始化所有被监控服务的状态为 UNKNOWN
        // 这对于后续计算全局宕机比例至关重要
        for (String serviceName : targetServiceNames) {
            appStatusStore.updateStatus(Map.of("id", serviceName, "name", formatDisplayName(serviceName), "status", "UNKNOWN"));
        }

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
        Object lock = serviceLocks.computeIfAbsent(serviceId, k -> new Object());
        String displayName = formatDisplayName(serviceId);

        synchronized (lock) {
            List<Instance> filteredInstances = namingEvent.getInstances().stream()
                    .filter(instance -> targetClusterSet.contains(instance.getClusterName()))
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
                // --- ✅ 更新点：更新全局宕机计数 ---
                if ("DOWN".equals(newStatus) && !"DOWN".equals(oldStatus)) {
                    downServiceCount.incrementAndGet(); // 状态变为 DOWN，计数器+1
                } else if (!"DOWN".equals(newStatus) && "DOWN".equals(oldStatus)) {
                    downServiceCount.decrementAndGet(); // 状态从 DOWN 恢复，计数器-1
                }
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
                    alertNotifier.sendMarkdownMessage(title, text, AlertLevel.DOWN);

                } else if ("RUNNING".equals(newStatus) && "DOWN".equals(oldStatus)) {
                    // 如果是从 DOWN 恢复到 RUNNING，发送恢复通知
                    String title = "✅ 服务恢复通知";
                    String text = String.format("#### %s\n\n> **服务名**: %s\n\n> **当前状态**: <font color='#008000'>**%s**</font>\n\n> **时间**: %s",
                            title, serviceId, newStatus, getCurrentTimestamp());
                    alertNotifier.sendMarkdownMessage(title, text, AlertLevel.RECOVERY);
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

    private String formatDisplayName(String serviceId) {
        return serviceId.endsWith(".app") ? serviceId.substring(0, serviceId.length() - 4) : serviceId;
    }

    /**
     * ✅ 更新点：新增方法，检查并发送全局告警
     */
    private void checkAndSendGlobalAlert() {
        if (totalMonitoredServices == 0) {
            return; // 如果监控的服务总数为0，则不执行
        }

        double currentDownRatio = (double) downServiceCount.get() / totalMonitoredServices;
        double threshold = monitoringProperties.getGlobalDownThreshold();

        // 1. 检查是否触发了宕机阈值
        if (currentDownRatio >= threshold) {
            // 如果超过阈值，并且告警 *尚未* 发送
            if (!isGlobalAlertSent) {
                String title = "🔥 系统重大告警";
                String text = String.format("#### %s\n\n> **警告**: 系统中 **%d%%** 的服务处于DOWN状态（%d/%d），已超过 **%.0f%%** 的阈值！\n\n> **请立即检查系统！**\n\n> **时间**: %s",
                        title, (int)(currentDownRatio * 100), downServiceCount.get(), totalMonitoredServices, threshold * 100, getCurrentTimestamp());

                // 使用全局 Webhook 发送
                alertNotifier.sendMarkdownMessage(title, text, monitoringProperties.getGlobalAlertWebhook(), AlertLevel.CRITICAL);

                isGlobalAlertSent = true; // 将标记设置为已发送
                logger.warn("全局告警阈值已触发 ({} DOWN / {} TOTAL = {}%)", downServiceCount.get(), totalMonitoredServices, (int)(currentDownRatio * 100));
            }
        }
        // 2. 检查是否从宕机状态中恢复
        else {
            // 如果低于阈值，并且告警 *之前* 发送过
            if (isGlobalAlertSent) {
                String title = " recoveries: 系统已恢复";
                String text = String.format("#### %s\n\n> **通知**: 系统已从重大告警中恢复。\n\n> **当前宕机比例**: **%d%%**（%d/%d），已低于 **%.0f%%** 的阈值。\n\n> **时间**: %s",
                        title, (int)(currentDownRatio * 100), downServiceCount.get(), totalMonitoredServices, threshold * 100, getCurrentTimestamp());

                // 同样使用全局 Webhook 发送
                alertNotifier.sendMarkdownMessage(title, text, monitoringProperties.getGlobalAlertWebhook(), AlertLevel.RECOVERY);

                isGlobalAlertSent = false; // 重置标记
                logger.info("全局告警状态已恢复 ({} DOWN / {} TOTAL = {}%)", downServiceCount.get(), totalMonitoredServices, (int)(currentDownRatio * 100));
            }
        }
    }
}
