-- V3: store everything a sync produces (Phase 7).
-- Takes V3, so the developer's activity migration (Phase 2.6) becomes V4__activity.sql.

-- Phase 5 replaced v1's roast with a supportive year in review.
ALTER TABLE insights RENAME COLUMN roast TO year_in_review;

ALTER TABLE insights
    ADD COLUMN repo_summaries   JSONB NOT NULL DEFAULT '{}'::jsonb, -- repo name -> one-sentence summary
    ADD COLUMN character_title  TEXT,                               -- D&D character sheet (LLM flavour)
    ADD COLUMN character_lore   TEXT,
    ADD COLUMN archetype_scores JSONB NOT NULL DEFAULT '{}'::jsonb, -- class name -> 0..100, for Phase 13 leaderboards
    ADD COLUMN algo_version     INT;                                -- scoring formula version; never rank across versions

-- When the last sync was started (claimed). Starts the per-user cooldown, even if that sync fails.
ALTER TABLE users ADD COLUMN sync_requested_at TIMESTAMPTZ;
