---
description: Implement and verify the recommended release upgrade feature
argument-hint: <optional-extra-constraint>
---

Use the `safe-release-change` skill.

Implement `recommended_upgrade(releases, current_version, on_date)` in `app/release_policy.py`.

- Reject an unknown current version with `ValueError("Unknown release: <version>")`
- Return `None` when the current release is still supported on `on_date`
- Otherwise return the version string of the supported release with the latest `support_ends` date
- Return `None` when no supported upgrade exists
- Preserve the input list
- Do not edit `acceptance/`, `.bob/`, `AGENTS.md`, or `.gitignore`
- Run both documented test commands before finishing

Extra constraint from the user: $1

