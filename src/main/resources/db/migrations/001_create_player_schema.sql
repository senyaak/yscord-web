CREATE TABLE IF NOT EXISTS queue_item (
    id BIGSERIAL PRIMARY KEY,
    "position" INT NOT NULL,
    video_id VARCHAR(32) NOT NULL,
    title TEXT NOT NULL,
    duration INT NOT NULL,
    uploader VARCHAR(512) NOT NULL,
    thumbnail TEXT NULL,
    webpage_url TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS player_state (
    id INT PRIMARY KEY,
    current_index INT NOT NULL,
    loop_mode VARCHAR(16) NOT NULL,
    volume DOUBLE PRECISION NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
