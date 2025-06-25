package com.mofari.heimdall.service;

import com.mofari.heimdall.websocket.AppStatusWebSocketServer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class WebSocketHeartbeatService {

    /**
     * 创建一个定时任务，每隔 30 秒执行一次。
     * fixedRate = 30000 毫秒
     */
    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        // 调用 WebSocket 广播站的静态 ping 方法
        AppStatusWebSocketServer.sendPing();
    }
}