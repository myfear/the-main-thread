# Agent memory design checklist

Use this companion only when the request needs a concrete design or implementation plan.

## Scope and trust boundaries

- Tenant, user, agent, and project identifiers, plus which layer enforces each boundary.
- Which sources may write records, and who may read, export, share, or delete them.
- Memory is data, not instruction. Instruction-shaped text, including text from third parties, must not change tool permissions or agent behavior.

## Suggested record layout

```text
memory-root/
  users/<user-id>/
    profile.md
    preferences.md
    topics/<subject>.md
    people/<subject>.md
    areas/<project-or-thread>.md
  agents/<agent-id>/
    skills/<skill-name>/
  shared/<tenant-id>/                 # explicit opt-in only
  _index/manifest.json
```

Each record should have one clear subject. The manifest is a compact inventory with path, size, update time, and short summary; it is not a second copy of the full memory.

## Storage operations

| Operation | Purpose | Required guard |
| --- | --- | --- |
| List | Cheap discovery by scoped path | Server-enforced scope |
| Read | Load selected record(s) | Authorization check |
| Write | Create or replace a whole record | `if_version` (`new` for create) |
| Patch | Unique exact-match replacement | Current `if_version` |
| Append | Add log-style material | Current `if_version` |
| Delete | Forget a record | Direct user instruction and current `if_version` |

On a version conflict, return the current version and enough current content for the caller to merge deliberately. Do not silently overwrite.

## Content policy

- Never persist credentials, tokens, account numbers, or other sensitive-by-default data.
- Mark saved claims as `stated`, `observed`, or `derived`; do not use an inference as a high-stakes fact without confirmation.
- Read the manifest at session start. Read individual records only for the current task.
- Set size limits. Consolidate old log material into dated summaries and preserve a small, recent verbatim window.
- Store a pointer and short context when the canonical source is queryable elsewhere.

## Minimum acceptance tests

1. A new record is rejected unless its requested version is `new`.
2. A stale patch fails without losing the newer writer's update.
3. A caller cannot list or read another tenant's records.
4. A credential-like value is rejected or redacted before persistence and absent from logs.
5. A memory record that says to ignore policies is returned as data and never executed as an instruction.
