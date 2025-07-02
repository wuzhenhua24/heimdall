package com.mofari.heimdall.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class AppInfoService {

    private static final Logger log = LoggerFactory.getLogger(AppInfoService.class);

    // 从 application.yml 注入接口地址模板
    @Value("${appinfo.api.detail-url}")
    private String detailApiUrl;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 根据应用名称获取其负责人列表
     * @param appName 应用名
     * @return 负责人列表，如果获取失败或无数据则返回空列表
     */
    public List<String> getAppOwners(String appName) {
        try {
            // 替换 URL 中的占位符
            String url = detailApiUrl.replace("{appName}", appName);
            String jsonResponse = restTemplate.getForObject(url, String.class);

            if (jsonResponse == null) {
                log.warn("API response for app '{}' was null.", appName);
                return Collections.emptyList();
            }

            // 使用 Jackson 的 JsonNode 安全地解析嵌套的 JSON
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode ownerNode = root.path("data").path("roleUsers").path("OWNER");

            if (ownerNode.isMissingNode() || !ownerNode.isArray()) {
                log.warn("OWNER field is missing or not an array in API response for app '{}'", appName);
                return Collections.emptyList();
            }

            // 遍历 JSON 数组，提取负责人名称
            List<String> owners = new ArrayList<>();
            for (JsonNode node : ownerNode) {
                owners.add(node.asText());
            }
            return owners;

        } catch (Exception e) {
            log.error("Failed to get app owners for '{}' from API.", appName, e);
            return Collections.emptyList(); // 发生任何异常都返回空列表，保证主流程不中断
        }
    }
}
