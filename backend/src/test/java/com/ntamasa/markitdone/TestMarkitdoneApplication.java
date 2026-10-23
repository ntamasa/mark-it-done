package com.ntamasa.markitdone;

import org.springframework.boot.SpringApplication;

public class TestMarkitdoneApplication {

  public static void main(String[] args) {
    SpringApplication.from(MarkitdoneApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
