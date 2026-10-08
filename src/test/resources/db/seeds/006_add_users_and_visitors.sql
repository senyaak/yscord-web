-- Data as it looked right after 006. Anonymous visitors only: app_user rows
-- stand for real Google accounts, so the seed doesn't invent any.
INSERT INTO visitor (id, created_at, last_seen_at, user_id) VALUES
    ('00000000-0000-4000-8000-000000000001', now() - interval '3 days', now() - interval '1 day', NULL),
    ('00000000-0000-4000-8000-000000000002', now(), now(), NULL);
