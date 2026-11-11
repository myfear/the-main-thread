# 7. Overtime pay for the January 2027 rollout

## Context

This fictional payroll has two rules: flat-rate contracts receive no separate
overtime (INC-412), and standard-contract overtime is rounded down to 15-minute
blocks for the existing export (PAY-88). Emergency dispatch bypasses the scheduling
validator, so the calculation still needs the flat-rate guard.

[PAY-501](../payroll-2027.md) approves exact-minute export and pay for standard
contracts from January 2027. Format support alone would not authorize changing pay.

## Decision

Keep the flat-rate guard and its emergency-dispatch regression check. Remove block
rounding in a separate commit for the January rollout, with a ten-minute check.

## Consequences

At rollout, a standard contract receives every minute above its weekly allowance,
including amounts below 15 minutes. A flat-rate contract receives zero separate
overtime on either caller path.

This branch models the January behavior. The lab has no date-based policy selector;
deploying it early would also change November payroll. Until rollout, use the
preceding behavior-preserving commit, which retains block rounding.

## Evidence

- INC-412: the 2019 guard commit and the deleted `PayrollExportChecks` regression.
- PAY-88: the 2021 block-rounding commit.
- [PAY-501: approved January rollout](../payroll-2027.md).
- `Checks.java`: flat-rate dispatch and exact-minute regression checks.
