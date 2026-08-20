package com.aivle.backend.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.review-quick-access")
public record ReviewQuickAccessProperties(
    boolean enabled,
    String userUsername,
    String adminUsername
) {
}
