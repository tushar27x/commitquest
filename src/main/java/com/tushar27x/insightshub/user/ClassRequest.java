package com.tushar27x.insightshub.user;

import com.tushar27x.insightshub.entity.Archetype;

/**
 * {@code PUT /api/user/hero/class}. {@code null}, or no {@code archetype} key at all, means follow the top score.
 * An unknown class name is a JSON error: Spring's own 400.
 */
public record ClassRequest(Archetype archetype) {
}
