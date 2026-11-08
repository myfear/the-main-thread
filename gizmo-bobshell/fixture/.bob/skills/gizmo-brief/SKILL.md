---
name: gizmo-brief
description: Prepare Gizmo's daily engineering brief from local memory, inbox, and calendar snapshots.
---

Read memory/, inbox/, and calendar/. Use the date and timezone in the request.
Write briefs/YYYY-MM-DD.md. Keep it under 650 words and include:

1. The most useful next action and the reason it matters.
2. Today's meetings, preparation, and any overlap, with times and participants.
3. Messages requiring a reply or decision, newest first; omit routine noise.
4. Open commitments already overdue or due within the next three calendar days,
   using only unique C- IDs and current deadlines from memory/commitments.md.
5. Conflicting or missing evidence, with sources and explicit uncertainty.
6. One focused question if a human decision blocks progress.

Use citations such as `memory/people/ada.md [P-ADA-1]`. Prepare a reply only
when useful; save it separately in drafts/ with an UNSENT label. Never imply
that a message, calendar change, or other external action happened. End with
the paths of the files you wrote. Do not modify memory during this workflow.
Project milestones and historical deadline changes are not extra delivery
commitments. Keep them in project context, separate from the ledger count.
