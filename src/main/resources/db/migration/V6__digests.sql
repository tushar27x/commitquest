-- V6: the daily digest (Phase 12). A digest groups the hero_events it covered: SENT went out on a channel,
-- SKIPPED was stamped without an email (nothing notable, alerts off, or older than a week), so unsent events
-- never pile up. hero_events.digest_id (V5) now points here.
CREATE TABLE digests (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (github_id) ON DELETE CASCADE,
    status      VARCHAR(8)  NOT NULL CHECK (status IN ('SENT', 'SKIPPED')),
    channel     VARCHAR(16),                                    -- = notify/NotificationChannel.name(), SENT only
    event_count INT         NOT NULL CHECK (event_count > 0),
    ai_intro    BOOLEAN     NOT NULL DEFAULT FALSE,             -- false: the static intro (AI down, or SKIPPED)
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),             -- for SENT: when the channel accepted it
    CHECK ((status = 'SENT') = (channel IS NOT NULL)),
    CHECK (status = 'SENT' OR NOT ai_intro)
);

-- "Was one sent on the user's local today?": the latest SENT digest per user.
CREATE INDEX idx_digests_user_sent ON digests (user_id, created_at DESC) WHERE status = 'SENT';

ALTER TABLE hero_events
    ADD CONSTRAINT hero_events_digest_id_fkey FOREIGN KEY (digest_id) REFERENCES digests (id);
