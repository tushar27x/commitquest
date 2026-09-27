package com.tushar27x.insightshub.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "insightshub")
public class InsightsHubProperties {
    @NotBlank String frontendUrl;
    @NotBlank String backendUrl;
    @NotBlank String encryptionKey;
    @NotBlank String jwtSecret;
}
