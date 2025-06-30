package com.mofari.heimdall.service;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.mofari.heimdall.websocket.AppStatusWebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NacosListenerService {
    private static final Logger logger = LoggerFactory.getLogger(NacosListenerService.class);

    @Autowired
    private NamingService namingService; // Nacos 客户端核心 API

    @Autowired
    private AppStatusStore appStatusStore; // ✅ 注入状态存储服务

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

                    String serviceId = namingEvent.getServiceName();
                    List<Instance> instances = namingEvent.getInstances();
                    // ✅ 使集群名称进行过滤
                    List<Instance> filteredInstances = instances.stream()
                            .filter(instance -> "daily-default".equals(instance.getClusterName()))
                            .collect(Collectors.toList());

                    String status = calculateStatus(filteredInstances);

                    String displayName = serviceId;
                    if (serviceId.endsWith(".app")) {
                        displayName = serviceId.substring(0, serviceId.length() -4);
                    }

                    logger.info("Service Change Detected: id = '{}', name = '{}', status= '{}' " ,serviceId, displayName, status);

                    // 创建一个消息对象
                    Map<String, Object> message = new HashMap<>();
                    message.put("id", serviceId); // 使用服务名作为ID
                    message.put("name", displayName);
                    message.put("status", status);

                    // 更新内存中的状态存储
                    appStatusStore.updateStatus(message);

                    // 通过 WebSocket 广播这个变化
                    AppStatusWebSocketServer.broadcast(message);
                }
            });
        }
        logger.info("Nacos listeners initialized for " + serviceNames.size() + " services.");
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
}
