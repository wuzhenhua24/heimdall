package com.mofari.heimdall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个专门用于映射 application.yml 中 'spring.monitoring' 前缀下所有配置的类。
 */
@ConfigurationProperties(prefix = "spring.monitoring")
@Data // 使用 Lombok 自动生成 getter, setter, toString, equals, hashCode 等方法
public class MonitoringProperties {

    /**
     * 要监控的目标集群列表。
     * 对应 YAML 中的 spring.monitoring.target-clusters
     */
    private List<String> targetClusters = new ArrayList<>();

    /**
     * 要排除的服务名关键词列表。
     * 对应 YAML 中的 spring.monitoring.exclude-service-keywords
     */
    private List<String> excludeServiceKeywords = new ArrayList<>();

    /**
     * 完全不进行监控的服务白名单。
     * 对应 YAML 中的 spring.monitoring.whitelist-services
     */
    private List<String> whitelistServices = new ArrayList<>();

    // ✅ 更新点：新增全局告警阈值，默认为30%
    private double globalDownThreshold = 0.30;

    // ✅ 更新点：新增全局告警专用的 Webhook 地址
    private String globalAlertWebhook;

}
