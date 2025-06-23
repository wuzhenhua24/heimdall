package com.mofari.heimdall.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.exception.NacosException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
public class NacosConfig {

    // 使用 @Value 注解从 application.yml 中注入 Nacos 的配置
    @Value("${spring.nacos.server-addr}")
    private String serverAddr;

    @Value("${spring.nacos.username}")
    private String username;

    @Value("${spring.nacos.password}")
    private String password;

    /**
     * 手动创建一个 NamingService 的 Bean
     * @return NamingService 实例
     * @throws NacosException 如果创建过程中发生错误
     */
    @Bean
    public NamingService namingService() throws NacosException {
        System.out.println("====== Manually Creating NamingService Bean ======");

        Properties properties = new Properties();
        properties.setProperty("serverAddr", serverAddr);
        properties.setProperty("username", username);
        properties.setProperty("password", password);

        // 使用 Nacos 提供的工厂类来创建实例
        NamingService namingService = NacosFactory.createNamingService(properties);

        System.out.println("====== NamingService Bean Created Successfully! ======");

        return namingService;
    }
}