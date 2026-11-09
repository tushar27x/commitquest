package com.tushar27x.insightshub.user;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * {@code insightshub.private.*} (B11).
 *
 * @param changeCooldown after an effective change to private access (either direction) the switch is locked this long
 */
@ConfigurationProperties(prefix = "insightshub.private")
public record PrivateProperties(@DefaultValue("7d") Duration changeCooldown) {
}
