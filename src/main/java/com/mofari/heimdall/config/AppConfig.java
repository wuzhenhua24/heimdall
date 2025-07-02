package com.mofari.heimdall.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    /**
     * 将 RestTemplate 注册为一个 Bean，由 Spring 容器管理。
     * 这样，在项目中的任何其他地方，你都可以通过 @Autowired 来注入它。
     * @return 一个 RestTemplate 实例
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
