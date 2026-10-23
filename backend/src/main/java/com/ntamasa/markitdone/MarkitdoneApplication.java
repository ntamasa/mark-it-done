package com.ntamasa.markitdone;

import com.ntamasa.markitdone.config.MarkitdoneProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(MarkitdoneProperties.class)
@EnableScheduling
public class MarkitdoneApplication {

  public static void main(String[] args) {
    SpringApplication.run(MarkitdoneApplication.class, args);
  }
}
