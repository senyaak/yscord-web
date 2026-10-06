-- Data as it looked right after 001: every later migration must cope with it.
INSERT INTO queue_item ("position", video_id, title, duration, uploader, thumbnail, webpage_url) VALUES
    (0, 'dQw4w9WgXcQ', 'First', 213, 'Uploader', NULL, 'https://youtu.be/dQw4w9WgXcQ'),
    (1, 'aaaaaaaaaaa', 'Second', 180, 'Uploader', 'https://i.ytimg.com/vi/aaaaaaaaaaa/hq.jpg', 'https://youtu.be/aaaaaaaaaaa');

INSERT INTO player_state (id, current_index, loop_mode, volume, updated_at) VALUES
    (1, 1, 'queue', 0.7, now());
