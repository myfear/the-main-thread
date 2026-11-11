package com.themainthread.payroll;

public final class OvertimeRules {

    static final long PAYROLL_BLOCK_MINUTES = 15;

    public long paidOvertimeMinutes(long workedMinutes, Contract contract) {
        if (contract.includesOvertime()) {
            // INC-412: flat-rate contracts are paid through their allowance.
            // Emergency dispatch bypasses ShiftAssignmentValidator.
            return 0;
        }
        long overtime = Math.max(0, workedMinutes - contract.weeklyMinutes());
        return overtime / PAYROLL_BLOCK_MINUTES * PAYROLL_BLOCK_MINUTES;
    }
}
