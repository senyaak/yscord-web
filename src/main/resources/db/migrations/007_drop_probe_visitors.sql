-- Data only. Until the visitor filter skipped /actuator, every kubelet probe
-- (no cookies) created a visitor: ~200k rows by 2026-10-24, one real login.
-- Probes are indistinguishable from a person seen exactly once without logging
-- in; nothing references visitors yet, so such a person only gets a new id on
-- the next visit. No down file: deleted rows can't come back.
DELETE FROM visitor
WHERE user_id IS NULL
  AND last_seen_at = created_at;
