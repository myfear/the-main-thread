# Cleanup proposal

## Removed the flat-rate guard

`ShiftAssignmentValidator` rejects flat-rate contracts for overtime-eligible
shifts, so `paidOvertimeMinutes` cannot receive that combination.

## Removed 15-minute rounding

`docs/payroll-2027.md` says the January 2027 export accepts minutes.

## Result

`./scripts/check.sh` passes 6 checks with 0 failures.
