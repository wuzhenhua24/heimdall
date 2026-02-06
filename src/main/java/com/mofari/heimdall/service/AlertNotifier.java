package com.mofari.heimdall.service;

public interface AlertNotifier {
    /**
     * Send a markdown-like alert message. Implementations can map this into
     * their own rich message format (e.g., Feishu post).
     */
    void sendMarkdownMessage(String title, String markdownText, AlertLevel level);

    /**
     * Send a markdown-like alert message to a specified webhook.
     */
    void sendMarkdownMessage(String title, String markdownText, String webhookUrl, AlertLevel level);
}
