ALTER TABLE game_match
    ADD COLUMN engine_digest VARCHAR(255) NOT NULL DEFAULT 'legacy-unknown',
    ADD COLUMN engine_policy_version VARCHAR(64) NOT NULL DEFAULT 'legacy-unknown';
