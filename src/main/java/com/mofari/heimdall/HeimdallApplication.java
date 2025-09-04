package com.mofari.heimdall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.mofari.heimdall.config.MonitoringProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(MonitoringProperties.class)
public class HeimdallApplication {

	public static void main(String[] args) {
		SpringApplication.run(HeimdallApplication.class, args);
	}

}