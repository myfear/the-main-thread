insert into stored_documents (id, external_id, owner_email, title, storage_key, checksum)
select nextval(sequence_name::regclass), 'DOC-1000', 'legal@parchment.example', 'Export declaration for batch 1000', 'docs/2026/06/DOC-1000.pdf', 'sha256:0b74fd3a6f4f7002'
from information_schema.sequences
where sequence_schema = current_schema()
order by sequence_name
fetch first 1 row only;

insert into stored_documents (id, external_id, owner_email, title, storage_key, checksum)
select nextval(sequence_name::regclass), 'DOC-1001', 'legal@parchment.example', 'Supplier invoice for route correction', 'docs/2026/06/DOC-1001.pdf', 'sha256:4c4cd9314b32300c'
from information_schema.sequences
where sequence_schema = current_schema()
order by sequence_name
fetch first 1 row only;

insert into stored_documents (id, external_id, owner_email, title, storage_key, checksum)
select nextval(sequence_name::regclass), 'DOC-2000', 'compliance@parchment.example', 'Retention hold notice for shipment 2000', 'docs/2026/06/DOC-2000.pdf', 'sha256:8d9f4401c0c9dd11'
from information_schema.sequences
where sequence_schema = current_schema()
order by sequence_name
fetch first 1 row only;
