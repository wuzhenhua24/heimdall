package com.mofari.heimdall.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 配置 CORS 规则
        registry.addMapping("/api/**") // ✅ 规则应用于所有以 /api/ 开头的路径
                .allowedOrigins(
                        "http://my-dash-board.mofari.com", // ✅ 允许你的前端生产环境域名
                        "http://localhost:5173"                    // ✅ (可选) 允许你的本地开发环境，方便调试
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 允许的 HTTP 方法
                .allowCredentials(true) // 是否允许发送 Cookie
                .maxAge(3600); // 预检请求（preflight request）的缓存时间（秒）
    }
}