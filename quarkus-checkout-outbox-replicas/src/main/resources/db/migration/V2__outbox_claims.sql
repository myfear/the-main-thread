ALTER TABLE outbox_event
    ADD COLUMN claimed_by TEXT,
    ADD COLUMN claim_token UUID,
    ADD COLUMN claimed_until TIMESTAMPTZ,
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;

DROP INDEX outbox_event_unpublished_idx;
CREATE INDEX outbox_event_pending_idx
    ON outbox_event (created_at, id)
    WHERE published_at IS NULL;
