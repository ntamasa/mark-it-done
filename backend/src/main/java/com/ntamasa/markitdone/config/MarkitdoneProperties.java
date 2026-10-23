package com.ntamasa.markitdone.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "markitdone")
public record MarkitdoneProperties(
    String appBaseUrl,
    String mailFrom,
    @NestedConfigurationProperty Jwt jwt,
    @NestedConfigurationProperty Cookies cookies,
    @NestedConfigurationProperty Recurrence recurrence) {
  public record Jwt(String secret, Duration accessTtl, Duration refreshTtl) {}

  public record Cookies(boolean secure) {}

  public record Recurrence(int horizonMonths) {}
}
