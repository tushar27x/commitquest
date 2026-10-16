-- V4: one row per user per day of GitHub activity (Part D, hero-pet plan §3).
-- A day is "active" (the hero is fed) when calendar_count > 0 OR branch_commit_count > 0.
-- The commits table once planned for Phase 2.6 is deferred.

CREATE TABLE daily_contributions (
    user_id             BIGINT NOT NULL REFERENCES users (github_id) ON DELETE CASCADE,
    date                DATE   NOT NULL,                              -- GitHub's calendar day
    calendar_count      INT    NOT NULL DEFAULT 0 CHECK (calendar_count >= 0),      -- contribution calendar
    branch_commit_count INT    NOT NULL DEFAULT 0 CHECK (branch_commit_count >= 0), -- branch scan (B3)
    PRIMARY KEY (user_id, date) -- one row per day; makes the sync's upsert idempotent
);
