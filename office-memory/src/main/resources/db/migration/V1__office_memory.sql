CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE office_project (
    id uuid PRIMARY KEY,
    name varchar(100) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO office_project(id, name) VALUES
('00000000-0000-0000-0000-000000000001', 'Team Offsite'),
('00000000-0000-0000-0000-000000000002', 'Leadership Briefing');
