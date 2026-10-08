-- Generated from AuthTables, then reviewed: reformatted, and dropped the
-- generator's CREATE SEQUENCE app_user_id_seq (BIGSERIAL already creates it).

-- People who logged in with Google, keyed by the stable "sub" claim.
CREATE TABLE IF NOT EXISTS app_user (
    id            BIGSERIAL PRIMARY KEY,
    google_sub    VARCHAR(255) NOT NULL,
    email         VARCHAR(320) NOT NULL,
    "name"        TEXT NULL,
    picture       TEXT NULL,
    created_at    TIMESTAMP NOT NULL,
    last_login_at TIMESTAMP NOT NULL,
    CONSTRAINT app_user_google_sub_unique UNIQUE (google_sub)
);

-- One browser (visitor_id cookie); user_id is set once it logs in.
CREATE TABLE IF NOT EXISTS visitor (
    id           UUID PRIMARY KEY,
    created_at   TIMESTAMP NOT NULL,
    last_seen_at TIMESTAMP NOT NULL,
    user_id      BIGINT NULL,
    CONSTRAINT fk_visitor_user_id__id FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX IF NOT EXISTS visitor_user_id ON visitor (user_id);
