package com.ntamasa.markitdone.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.time.Duration;

@ConfigurationProperties(prefix = "markitdone")
public record MarkitdoneProperties(
    String appBaseUrl,
    String mailFrom,
    @NestedConfigurationProperty
    Jwt jwt,
    @NestedConfigurationProperty
    Cookies cookies,
    @NestedConfigurationProperty
    Recurrence recurrence
) {
    public record Jwt(
        String secret,
        Duration accessTtl,
        Duration refreshTtl
    ) {}

    public record Cookies(
        boolean secure
    ) {}

    public record Recurrence(
        int horizonMonths
    ) {}
}
