CREATE UNIQUE INDEX unique_pending_proposal ON memory_proposal(project_id, dedup_key) WHERE status='PENDING';
