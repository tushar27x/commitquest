package com.tushar27x.insightshub.insights;

import com.tushar27x.insightshub.entity.Archetype;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Every archetype's score (0–100) and the primary archetype derived from them.
 *
 * @param algoVersion version of the scoring formula; scores from different versions must not be ranked together
 */
public record ArchetypeScores(Archetype primary, Map<Archetype, Integer> scores, int algoVersion) {

    public ArchetypeScores {
        EnumMap<Archetype, Integer> ordered = new EnumMap<>(Archetype.class); // declaration order
        ordered.putAll(scores);
        scores = Collections.unmodifiableMap(ordered);
    }
}
