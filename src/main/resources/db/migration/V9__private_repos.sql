-- V9 (B10): the opt-in to read private repos. The scopes column is what the host actually granted at the last login
-- or reported on the last call (comma-separated, e.g. 'read:user,user:email,repo'); NULL = never recorded, which
-- is read as "no repo scope". include_private is the user's choice and defaults to off for everyone. Schema v2 only.
ALTER TABLE users ADD COLUMN github_scopes TEXT;
ALTER TABLE users ADD COLUMN include_private BOOLEAN NOT NULL DEFAULT FALSE;
