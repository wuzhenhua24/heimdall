package com.mofari.heimdall.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class DingTalkNotifierService {

    private static final Logger log = LoggerFactory.getLogger(DingTalkNotifierService.class);

    // 从配置文件中注入 Webhook 地址
    @Value("${dingtalk.webhook}")
    private String webhookUrl;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 发送 Markdown 格式的钉钉消息
     * @param title 消息标题
     * @param markdownText Markdown 格式的消息内容
     */
    public void sendMarkdownMessage(String title, String markdownText) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn("钉钉 Webhook 未配置，跳过发送消息。");
            return;
        }

        try {
            // 构造请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // 构造请求体
            Map<String, Object> markdown = new HashMap<>();
            markdown.put("title", title);
            markdown.put("text", markdownText);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("msgtype", "markdown");
            requestBody.put("markdown", markdown);

            // 将请求体转为 JSON 字符串
            String jsonBody = objectMapper.writeValueAsString(requestBody);

            // 创建 HTTP 请求实体
            HttpEntity<String> requestEntity = new HttpEntity<>(jsonBody, headers);

            // 发送 POST 请求
            String response = restTemplate.postForObject(webhookUrl, requestEntity, String.class);
            log.info("成功发送钉钉消息，响应: {}", response);

        } catch (Exception e) {
            log.error("发送钉钉消息时发生错误", e);
        }
    }
}
