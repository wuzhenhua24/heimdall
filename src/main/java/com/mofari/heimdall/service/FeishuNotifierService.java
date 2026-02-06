package com.mofari.heimdall.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "spring.monitoring", name = "alert-channel", havingValue = "feishu", matchIfMissing = true)
public class FeishuNotifierService implements AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(FeishuNotifierService.class);

    @Value("${feishu.webhook:}")
    private String defaultWebhook;

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public void sendMarkdownMessage(String title, String markdownText, AlertLevel level) {
        sendMarkdownMessage(title, markdownText, this.defaultWebhook, level);
    }

    @Override
    public void sendMarkdownMessage(String title, String markdownText, String webhookUrl, AlertLevel level) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn("Webhook URL is not configured. Skipping Feishu message.");
            return;
        }

        try {
            String content = normalizeToLarkMarkdown(markdownText);
            Map<String, Object> requestBody = buildInteractiveCard(title, content, level);
            restTemplate.postForObject(webhookUrl, requestBody, String.class);
            log.info("Successfully sent Feishu message to {}", webhookUrl.substring(0, Math.min(30, webhookUrl.length())));
        } catch (Exception e) {
            log.error("Failed to send Feishu message: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildInteractiveCard(String title, String content, AlertLevel level) {
        Map<String, Object> headerTitle = new HashMap<>();
        headerTitle.put("tag", "plain_text");
        headerTitle.put("content", title);

        Map<String, Object> header = new HashMap<>();
        header.put("title", headerTitle);
        header.put("template", mapHeaderTemplate(level));

        Map<String, Object> text = new HashMap<>();
        text.put("tag", "lark_md");
        text.put("content", content);

        Map<String, Object> div = new HashMap<>();
        div.put("tag", "div");
        div.put("text", text);

        List<Object> elements = new ArrayList<>();
        elements.add(div);

        Map<String, Object> config = new HashMap<>();
        config.put("wide_screen_mode", true);

        Map<String, Object> card = new HashMap<>();
        card.put("config", config);
        card.put("header", header);
        card.put("elements", elements);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("msg_type", "interactive");
        requestBody.put("card", card);
        return requestBody;
    }

    private String mapHeaderTemplate(AlertLevel level) {
        if (level == null) {
            return "blue";
        }
        switch (level) {
            case CRITICAL:
            case DOWN:
                return "red";
            case RECOVERY:
                return "green";
            case INFO:
            default:
                return "blue";
        }
    }

    private String normalizeToLarkMarkdown(String markdownText) {
        if (markdownText == null) {
            return "";
        }
        String text = markdownText;
        text = text.replaceAll("(?i)<font[^>]*>", "");
        text = text.replaceAll("(?i)</font>", "");
        text = text.replaceFirst("^\\s*####\\s+.*\\n*", "");
        text = text.replaceAll("(?m)^>\\s?", "");
        text = text.replaceAll("\\n{3,}", "\n\n");
        text = text.trim();
        return text;
    }
}
