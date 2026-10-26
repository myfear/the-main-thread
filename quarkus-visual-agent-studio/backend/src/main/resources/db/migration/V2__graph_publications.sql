CREATE TABLE studio.graph_publication (
    id UUID PRIMARY KEY,
    flow_id UUID NOT NULL,
    version TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PUBLISHING', 'READY', 'FAILED_TO_DEPLOY')),
    compiled_json JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (flow_id, version)
);

CREATE TABLE studio.graph_run (
    id UUID PRIMARY KEY,
    publication_id UUID NOT NULL REFERENCES studio.graph_publication(id),
    engine_instance_id VARCHAR(26) UNIQUE,
    start_status TEXT NOT NULL CHECK (start_status IN ('STARTING', 'RUNNING', 'START_UNCERTAIN')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
