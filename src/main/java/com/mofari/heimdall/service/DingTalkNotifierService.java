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

    @Value("${dingtalk.webhook}") // 注入单个应用告警的 Webhook
    private String defaultWebhook;

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
        sendMarkdownMessage(title, markdownText, this.defaultWebhook);
    }

    /**
     * ✅ 更新点：新增一个重载方法，允许动态指定 Webhook URL
     *
     * @param title      消息标题
     * @param text       Markdown 格式的消息内容
     * @param webhookUrl 目标 Webhook 地址
     */
    public void sendMarkdownMessage(String title, String text, String webhookUrl) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn("Webhook URL is not configured. Skipping DingTalk message.");
            return;
        }

        try {
            Map<String, Object> markdown = new HashMap<>();
            markdown.put("title", title);
            markdown.put("text", text);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("msgtype", "markdown");
            requestBody.put("markdown", markdown);

            // 发送消息
            restTemplate.postForObject(webhookUrl, requestBody, String.class);
            log.info("Successfully sent DingTalk message to {}", webhookUrl.substring(0, 30)); // 截断URL防日志刷屏
        } catch (Exception e) {
            log.error("Failed to send DingTalk message: " + e.getMessage(), e);
        }
    }
}
