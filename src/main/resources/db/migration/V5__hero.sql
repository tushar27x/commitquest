-- V5: the hero (Part D, hero-pet plan §2 and §3). HeroRules (Java) owns the game rules; the CHECKs
-- below only refuse states those rules can never produce.

-- users: the settings the hero job needs.
ALTER TABLE users
    ADD COLUMN timezone             VARCHAR(64) NOT NULL DEFAULT 'UTC', -- IANA, set from the browser on first login
    -- Rest days: bit 0 = Monday ... bit 6 = Sunday, at most 2 set. A change applies from the next local day,
    -- so the previous mask and the first day of the new one are kept for catching up on older days.
    ADD COLUMN rest_days            SMALLINT    NOT NULL DEFAULT 0,
    ADD COLUMN rest_days_previous   SMALLINT    NOT NULL DEFAULT 0,
    ADD COLUMN rest_days_from       DATE,                                -- null: never changed
    ADD COLUMN chosen_archetype     VARCHAR(32),                         -- null: follow the top-scored class
    ADD COLUMN archetype_changed_at TIMESTAMPTZ;                         -- starts the 7-day switch cooldown

-- x & (x - 1) clears the lowest set bit: twice leaves 0 only if at most 2 bits were set.
ALTER TABLE users
    ADD CONSTRAINT users_rest_days_check CHECK (
        rest_days BETWEEN 0 AND 127
        AND ((rest_days & (rest_days - 1)) & ((rest_days & (rest_days - 1)) - 1)) = 0),
    ADD CONSTRAINT users_rest_days_previous_check CHECK (
        rest_days_previous BETWEEN 0 AND 127
        AND ((rest_days_previous & (rest_days_previous - 1)) & ((rest_days_previous & (rest_days_previous - 1)) - 1)) = 0),
    -- Must list exactly the constants of entity/Archetype.java, like insights_archetype_check
    ADD CONSTRAINT users_chosen_archetype_check CHECK (chosen_archetype IN (
        'PALADIN', 'BARBARIAN', 'WIZARD', 'ARTIFICER',
        'CLERIC', 'BARD', 'MONK', 'ROGUE'));

-- users.no_commit_days is obsolete (hearts replace "no commits for N days"); left in place, unused.

-- One hero per user, created on first login (seeded from the last 90 days).
CREATE TABLE hero_state (
    user_id           BIGINT      PRIMARY KEY REFERENCES users (github_id) ON DELETE CASCADE,
    level             INT         NOT NULL DEFAULT 1 CHECK (level >= 1),
    xp                INT         NOT NULL DEFAULT 0,                       -- progress inside the level
    lives             INT         NOT NULL DEFAULT 3 CHECK (lives BETWEEN 1 AND 3),
    streak            INT         NOT NULL DEFAULT 0 CHECK (streak >= 0),
    last_evaluated_on DATE        NOT NULL,                                 -- makes catch-up idempotent
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (xp >= 0 AND xp < 50 * level)                                     -- level n -> n+1 takes 50 x n
);

-- What happened, for the Home and Journal screens and the digest (replaces the planned habit_alerts).
CREATE TABLE hero_events (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (github_id) ON DELETE CASCADE,
    type       VARCHAR(16) NOT NULL CHECK (type IN (                        -- = hero/HeroEventType.java
                   'LIFE_LOST', 'LIFE_GAINED', 'LEVEL_UP', 'LEVEL_DOWN',
                   'FAINTED', 'RESTED', 'CLASS_CHANGED')),
    on_date    DATE        NOT NULL,                                        -- the user's local day
    details    JSONB       NOT NULL DEFAULT '{}'::jsonb,
    digest_id  BIGINT,                                                      -- null until a digest sent it (Phase 12)
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, type, on_date)                                         -- a re-run can't add the same event twice
);

CREATE INDEX idx_hero_events_user_date ON hero_events (user_id, on_date); -- GET /hero/events?days=N
CREATE INDEX idx_hero_events_unsent ON hero_events (user_id) WHERE digest_id IS NULL;

-- One row per settled day: the xp_days of GET /hero/events (events alone can't rebuild XP and streak).
CREATE TABLE hero_days (
    user_id   BIGINT  NOT NULL REFERENCES users (github_id) ON DELETE CASCADE,
    on_date   DATE    NOT NULL,
    xp_gained INT     NOT NULL CHECK (xp_gained >= 0),
    streak    INT     NOT NULL CHECK (streak >= 0),                         -- after the day
    PRIMARY KEY (user_id, on_date)                                          -- re-runs are harmless
);
