package com.ntamasa.markitdone.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides the clock used to find "today"; tests can replace it. */
@Configuration
public class ClockConfig {

  /**
   * The system clock in UTC.
   *
   * @return the clock
   */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
