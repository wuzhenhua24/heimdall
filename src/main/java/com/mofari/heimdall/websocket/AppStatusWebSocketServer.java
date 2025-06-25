package com.mofari.heimdall.websocket;

// ✅ 导入 jakarta 命名空间下的 WebSocket API
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.CrossOrigin;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@CrossOrigin // ✅ 在类上添加这个注解，它会使用你的全局配置或默认允许所有来源
@ServerEndpoint("/api/v1/status") // 定义WebSocket的访问路径
public class AppStatusWebSocketServer {

    // 用于存放所有连接的客户端
    private static final Map<String, Session> clients = new ConcurrentHashMap<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @OnOpen
    public void onOpen(Session session) {
        clients.put(session.getId(), session);
        log.info("New connection: " + session.getId());
        // 可以在新连接建立时，主动发送一次全量数据
        // sendFullStatusData(session);
    }

    @OnClose
    public void onClose(Session session) {
        clients.remove(session.getId());
        log.info("Connection closed: " + session.getId());
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.info("Error for session " + session.getId() + ": " + error.getMessage());
        error.printStackTrace();
    }

    /**
     * 广播消息给所有连接的客户端
     * @param messagePayload 要发送的消息对象
     */
    public static void broadcast(Object messagePayload) {
        try {
            String message = objectMapper.writeValueAsString(messagePayload);
            for (Session session : clients.values()) {
                if (session.isOpen()) {
                    session.getBasicRemote().sendText(message);
                }
            }
        } catch (IOException e) {
            log.info("Broadcast failed: " + e.getMessage());
        }
    }

    /**
     * ✅ 新增：发送一个 "ping" 消息给所有客户端以保持连接活跃。
     */
    public static void sendPing() {
        if (clients.isEmpty()) {
            return;
        }

        // 我们发送一个简单的 JSON 对象作为 ping 消息
        // 前端可以忽略这个消息，它的目的只是为了产生网络流量
        final String pingMessage = "{\"type\":\"ping\"}";

        // log.debug("Pinging {} clients...", clients.size()); // 如果想看日志可以取消注释
        for (Session session : clients.values()) {
            try {
                if (session.isOpen()) {
                    session.getBasicRemote().sendText(pingMessage);
                }
            } catch (IOException e) {
                log.warn("Failed to send ping to session {}: {}", session.getId(), e.getMessage());
            }
        }
    }
}
