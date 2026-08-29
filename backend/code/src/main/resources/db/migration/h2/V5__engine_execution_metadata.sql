ALTER TABLE game_match
    ADD COLUMN engine_digest VARCHAR(255) DEFAULT 'legacy-unknown' NOT NULL;

ALTER TABLE game_match
    ADD COLUMN engine_policy_version VARCHAR(64) DEFAULT 'legacy-unknown' NOT NULL;
