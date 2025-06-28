package com.mofari.heimdall.service;

import com.mofari.heimdall.websocket.AppStatusWebSocketServer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class WebSocketHeartbeatService {

    /**
     * 创建一个定时任务，每隔 30 秒执行一次。
     * 这个间隔应该小于 Nginx 和其他网络设备的空闲超时时间。
     */
    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        // 调用 WebSocket 广播站的静态 ping 方法
        // 这个方法发送的是标准的 PING 控制帧
        AppStatusWebSocketServer.sendPing();
    }
}