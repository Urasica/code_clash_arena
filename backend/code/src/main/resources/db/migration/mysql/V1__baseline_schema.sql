CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(255) NOT NULL,
    password VARCHAR(255),
    nickname VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    provider VARCHAR(255),
    provider_id VARCHAR(255),
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS game_match (
    id BIGINT NOT NULL AUTO_INCREMENT,
    match_uuid VARCHAR(255) NOT NULL,
    game_type VARCHAR(255) NOT NULL,
    mode VARCHAR(255) NOT NULL,
    map_data TEXT,
    played_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_game_match PRIMARY KEY (id),
    CONSTRAINT uk_game_match_uuid UNIQUE (match_uuid)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS match_player (
    id BIGINT NOT NULL AUTO_INCREMENT,
    game_match_id BIGINT NOT NULL,
    user_id BIGINT,
    player_index VARCHAR(255) NOT NULL,
    result VARCHAR(255) NOT NULL,
    score INTEGER NOT NULL,
    submitted_code TEXT,
    language VARCHAR(255),
    CONSTRAINT pk_match_player PRIMARY KEY (id),
    CONSTRAINT fk_match_player_match FOREIGN KEY (game_match_id) REFERENCES game_match (id),
    CONSTRAINT fk_match_player_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS match_replay (
    id BIGINT NOT NULL AUTO_INCREMENT,
    full_log LONGTEXT NOT NULL,
    game_match_id BIGINT NOT NULL,
    CONSTRAINT pk_match_replay PRIMARY KEY (id),
    CONSTRAINT fk_match_replay_match FOREIGN KEY (game_match_id) REFERENCES game_match (id)
) ENGINE=InnoDB;

UPDATE game_match SET map_data = '{"legacy":true}' WHERE map_data IS NULL;
UPDATE match_player SET submitted_code = '' WHERE submitted_code IS NULL;
UPDATE match_player SET language = 'unknown' WHERE language IS NULL OR language = '';

ALTER TABLE game_match MODIFY map_data TEXT NOT NULL;
ALTER TABLE match_player MODIFY submitted_code TEXT NOT NULL;
ALTER TABLE match_player MODIFY language VARCHAR(255) NOT NULL;

SET @has_index = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'users' AND index_name = 'idx_users_role_created_at');
SET @ddl = IF(@has_index = 0, 'CREATE INDEX idx_users_role_created_at ON users (role, created_at)', 'SELECT 1');
PREPARE statement FROM @ddl;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @has_index = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'game_match' AND index_name = 'idx_game_match_played_at');
SET @ddl = IF(@has_index = 0, 'CREATE INDEX idx_game_match_played_at ON game_match (played_at)', 'SELECT 1');
PREPARE statement FROM @ddl;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @has_index = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'match_player' AND index_name = 'idx_match_player_user');
SET @ddl = IF(@has_index = 0, 'CREATE INDEX idx_match_player_user ON match_player (user_id)', 'SELECT 1');
PREPARE statement FROM @ddl;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @has_unique = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'match_player' AND index_name = 'uk_match_player_role');
SET @ddl = IF(@has_unique = 0, 'ALTER TABLE match_player ADD CONSTRAINT uk_match_player_role UNIQUE (game_match_id, player_index)', 'SELECT 1');
PREPARE statement FROM @ddl;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @has_unique = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'match_replay' AND non_unique = 0 AND column_name = 'game_match_id');
SET @ddl = IF(@has_unique = 0, 'ALTER TABLE match_replay ADD CONSTRAINT uk_match_replay_match UNIQUE (game_match_id)', 'SELECT 1');
PREPARE statement FROM @ddl;
EXECUTE statement;
DEALLOCATE PREPARE statement;
