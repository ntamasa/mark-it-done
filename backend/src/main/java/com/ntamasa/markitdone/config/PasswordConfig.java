package com.ntamasa.markitdone.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    // Create Argon2 encoder with Spring Security 5.8 defaults
    // salt: 16, hash: 32, parallelism: 1, memory: 16384 KiB, iterations: 2
    Argon2PasswordEncoder argon2Encoder =
        new Argon2PasswordEncoder(
            16, // saltLength
            32, // hashLength
            1, // parallelism
            16384, // memory in KiB
            2 // iterations
            );

    // Create DelegatingPasswordEncoder with argon2 as default
    Map<String, PasswordEncoder> encoders = new HashMap<>();
    encoders.put("argon2", argon2Encoder);

    return new DelegatingPasswordEncoder("argon2", encoders);
  }
}
