CREATE TABLE users (
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
);

CREATE INDEX idx_users_role_created_at ON users (role, created_at);

CREATE TABLE game_match (
    id BIGINT NOT NULL AUTO_INCREMENT,
    match_uuid VARCHAR(255) NOT NULL,
    game_type VARCHAR(255) NOT NULL,
    mode VARCHAR(255) NOT NULL,
    map_data TEXT NOT NULL,
    played_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_game_match PRIMARY KEY (id),
    CONSTRAINT uk_game_match_uuid UNIQUE (match_uuid)
);

CREATE INDEX idx_game_match_played_at ON game_match (played_at);

CREATE TABLE match_player (
    id BIGINT NOT NULL AUTO_INCREMENT,
    game_match_id BIGINT NOT NULL,
    user_id BIGINT,
    player_index VARCHAR(255) NOT NULL,
    result VARCHAR(255) NOT NULL,
    score INTEGER NOT NULL,
    submitted_code TEXT NOT NULL,
    language VARCHAR(255) NOT NULL,
    CONSTRAINT pk_match_player PRIMARY KEY (id),
    CONSTRAINT uk_match_player_role UNIQUE (game_match_id, player_index),
    CONSTRAINT fk_match_player_match FOREIGN KEY (game_match_id) REFERENCES game_match (id),
    CONSTRAINT fk_match_player_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_match_player_user ON match_player (user_id);

CREATE TABLE match_replay (
    id BIGINT NOT NULL AUTO_INCREMENT,
    full_log LONGTEXT NOT NULL,
    game_match_id BIGINT NOT NULL,
    CONSTRAINT pk_match_replay PRIMARY KEY (id),
    CONSTRAINT uk_match_replay_match UNIQUE (game_match_id),
    CONSTRAINT fk_match_replay_match FOREIGN KEY (game_match_id) REFERENCES game_match (id)
);
