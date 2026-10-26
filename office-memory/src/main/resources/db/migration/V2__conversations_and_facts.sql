CREATE TABLE conversation (
    id uuid PRIMARY KEY, project_id uuid NOT NULL REFERENCES office_project(id),
    title varchar(120) NOT NULL, created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL, version bigint NOT NULL
);
CREATE TABLE conversation_message (
    id uuid PRIMARY KEY, conversation_id uuid NOT NULL REFERENCES conversation(id),
    role varchar(12) NOT NULL CHECK (role IN ('USER','ASSISTANT')),
    content text NOT NULL, created_at timestamptz NOT NULL, sequence bigint NOT NULL,
    request_id uuid, reply_to_id uuid REFERENCES conversation_message(id),
    state varchar(16) NOT NULL CHECK (state IN ('PROCESSING','COMPLETE','FAILED')),
    UNIQUE (conversation_id, sequence), UNIQUE (conversation_id, request_id)
);
CREATE TABLE chat_memory_snapshot (
    memory_id uuid PRIMARY KEY REFERENCES conversation(id), messages_json text NOT NULL,
    updated_at timestamptz NOT NULL, version bigint NOT NULL
);
CREATE TABLE memory_record (
    id uuid PRIMARY KEY, fact_id uuid NOT NULL,
    project_id uuid NOT NULL REFERENCES office_project(id),
    kind varchar(16) NOT NULL CHECK (kind IN ('FACT','DECISION','PREFERENCE')),
    text varchar(1000) NOT NULL, embedding vector(768) NOT NULL, metadata jsonb NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('ACTIVE','SUPERSEDED')),
    source_conversation_id uuid NOT NULL REFERENCES conversation(id),
    source_message_id uuid NOT NULL REFERENCES conversation_message(id),
    created_at timestamptz NOT NULL, superseded_at timestamptz, superseded_by_id uuid,
    version bigint NOT NULL
);
CREATE UNIQUE INDEX one_active_revision ON memory_record(project_id, fact_id) WHERE status='ACTIVE';
CREATE INDEX scoped_facts ON memory_record(project_id, status);
CREATE INDEX fact_history ON memory_record(project_id, fact_id, created_at);
CREATE TABLE memory_proposal (
    id uuid PRIMARY KEY, project_id uuid NOT NULL REFERENCES office_project(id), fact_id uuid NOT NULL,
    source_conversation_id uuid NOT NULL REFERENCES conversation(id),
    source_message_id uuid NOT NULL REFERENCES conversation_message(id),
    operation varchar(16) NOT NULL CHECK (operation IN ('REMEMBER','SUPERSEDE','FORGET')),
    target_memory_id uuid, target_version bigint, proposed_text varchar(1000),
    kind varchar(16) NOT NULL CHECK (kind IN ('FACT','DECISION','PREFERENCE')),
    reason varchar(500), status varchar(16) NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    dedup_key varchar(64) NOT NULL, result_memory_id uuid,
    created_at timestamptz NOT NULL, decided_at timestamptz, version bigint NOT NULL,
    UNIQUE(project_id, source_message_id, dedup_key)
);
CREATE INDEX pending_proposals ON memory_proposal(project_id, status);
CREATE TABLE memory_audit_event (
    id uuid PRIMARY KEY, project_id uuid NOT NULL REFERENCES office_project(id),
    proposal_id uuid NOT NULL REFERENCES memory_proposal(id), memory_id uuid,
    action varchar(24) NOT NULL, occurred_at timestamptz NOT NULL, actor varchar(16) NOT NULL
);
CREATE TABLE tool_invocation (
    id uuid PRIMARY KEY, conversation_id uuid NOT NULL REFERENCES conversation(id),
    source_message_id uuid NOT NULL REFERENCES conversation_message(id),
    tool_name varchar(48) NOT NULL, occurred_at timestamptz NOT NULL,
    duration_ms bigint NOT NULL, outcome varchar(24) NOT NULL, description varchar(120) NOT NULL
);
CREATE INDEX ordered_conversations ON conversation(project_id, updated_at);
CREATE INDEX ordered_messages ON conversation_message(conversation_id, sequence);
CREATE INDEX turn_tools ON tool_invocation(source_message_id, occurred_at);
