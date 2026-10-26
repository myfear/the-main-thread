CREATE TABLE studio.runtime_event (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    engine_instance_id VARCHAR(26) NOT NULL,
    run_id UUID NOT NULL,
    workflow_namespace TEXT NOT NULL,
    workflow_name TEXT NOT NULL,
    workflow_version TEXT NOT NULL,
    kind TEXT NOT NULL,
    task_identity TEXT,
    attempt INTEGER NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    output_json JSONB
);

CREATE INDEX studio_runtime_event_instance_idx ON studio.runtime_event (engine_instance_id, id);
CREATE INDEX studio_runtime_event_run_idx ON studio.runtime_event (run_id, id);
