-- V10 (B11): the 7-day lock on the private-repos switch and a flag for a pending full resync. Schema v2 only.
-- private_changed_at: when the user last made an effective change (NULL = never; the first change is always allowed).
-- private_resync_due: internal. Set when private access changed but the full resync did not (a failed login sync, or
-- a lost scope seen by an activity-only job); the next sync runs as a full resync and clears it.
ALTER TABLE users ADD COLUMN private_changed_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN private_resync_due BOOLEAN NOT NULL DEFAULT FALSE;
