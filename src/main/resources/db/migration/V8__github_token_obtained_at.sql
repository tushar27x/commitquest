-- V8 (B12, for B43): when the stored GitHub token was obtained (set at each login, which stores a new token), so a
-- GitHub 401 can be pinned to one token by its age. Never the token itself. NULL for tokens stored before V8.
-- Needs V7 (B11) first: Flyway refuses a V7 that arrives after V8 has run.
ALTER TABLE users ADD COLUMN github_token_obtained_at TIMESTAMPTZ;
