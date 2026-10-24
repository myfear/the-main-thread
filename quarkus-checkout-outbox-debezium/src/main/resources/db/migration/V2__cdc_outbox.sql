DROP INDEX outbox_event_unpublished_idx;
ALTER TABLE outbox_event DROP COLUMN published_at;
