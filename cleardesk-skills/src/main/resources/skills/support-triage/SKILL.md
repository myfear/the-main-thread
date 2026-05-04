---
name: support-triage
description: Customer-facing support desk — ticket intake, severity classification, and routing incidents that affect end users (not money movement and not build pipelines).
---

When this skill applies, the user is asking about **user-visible outages**, **SLAs**, **ticket numbers**, or **customer impact**.

After you activate this skill, call `routeToSupport` with a one-line reason. Then follow up using support-only tools if needed.

Do not use this skill for invoice disputes, refunds, or payment processors — that is finance. Do not use it for CI, Kubernetes, or deploy pipelines — that is devops.
