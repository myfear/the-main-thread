# Solution notes

`solution/from-cleanup` starts from the deliberately incorrect cleanup proposal.

1. `Preserve payroll behavior after cleanup` restores the flat-rate guard and
   block rounding, adds the emergency-dispatch regression check, and removes the
   proposal file. Only the redundant threshold early return stays removed.
2. `Apply per-minute overtime for January rollout (PAY-501)` removes block rounding,
   adds the ten-minute check, and records the effective date and decision.

The first commit preserves November behavior and has seven passing checks. The
second models January behavior and has eight. The lab has no date-based switch;
do not treat the final method as November's payroll rule.
