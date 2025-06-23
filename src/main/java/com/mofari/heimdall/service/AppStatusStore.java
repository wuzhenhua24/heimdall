package com.mofari.heimdall.service;

import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AppStatusStore {

    /**
     * 使用 ConcurrentHashMap 作为线程安全的内存状态存储。
     * Key: service name (e.g., "UserService")
     * Value: a Map representing the service's status info
     */
    private final Map<String, Map<String, Object>> statusCache = new ConcurrentHashMap<>();

    /**
     * 更新或添加一个服务的状态
     * @param serviceStatus 包含 id, name, status 的 Map
     */
    public void updateStatus(Map<String, Object> serviceStatus) {
        if (serviceStatus != null && serviceStatus.containsKey("id")) {
            statusCache.put((String) serviceStatus.get("id"), serviceStatus);
        }
    }

    /**
     * 获取所有服务的状态列表
     * @return 状态列表
     */
    public Collection<Map<String, Object>> getAllStatuses() {
        return statusCache.values();
    }

    /**
     * (可选) 获取单个服务的状态
     * @param serviceId 服务ID
     * @return 单个服务的状态
     */
    public Map<String, Object> getStatus(String serviceId) {
        return statusCache.get(serviceId);
    }
}