package com.ntamasa.markitdone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.ntamasa.markitdone.config.MarkitdoneProperties;

@SpringBootApplication
@EnableConfigurationProperties(MarkitdoneProperties.class)
@EnableScheduling
public class MarkitdoneApplication {

	public static void main(String[] args) {
		SpringApplication.run(MarkitdoneApplication.class, args);
	}

}
