---
name: gizmo-weekly
description: Review Gizmo's engineering project memory for progress, open promises, contradictions, and stale evidence.
---

Read memory/, inbox/, and calendar/. Write briefs/weekly-YYYY-MM-DD.md using
the requested date and timezone. Cite sources for progress and decisions.
Read memory/commitments.md and count unique Current rows with Status `open`.
Write `Open commitments: N` on its own line, replacing N with that count.
Under `## Open commitments`, include one Markdown table row per open ID,
with columns `ID`, `Commitment`, `Current due`, and `Last confirmed`.
Copy the stable IDs and current due dates accurately; include the timezone.
Use history to explain missed or revised dates, never as another active row
or as another promise in the opening count. Keep project milestones separate.
Do not treat a planned rollout as an additional delivery commitment unless
it has its own ledger ID. Qualify overdue status as of the evidence timestamp.

Also list contradictions still unresolved and notes
last confirmed more than 30 days ago. Missing recent confirmation means stale
or UNCONFIRMED, never automatically false. Describe next week's priorities in
three lines. Do not delete, repair, or silently refresh memory. Distinguish
evidence of completed work from plans and unsent drafts.
