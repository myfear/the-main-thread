package com.themainthread.payroll;

public final class OvertimeRules {

    static final long PAYROLL_BLOCK_MINUTES = 15;

    public long paidOvertimeMinutes(long workedMinutes, Contract contract) {
        if (contract.includesOvertime()) {
            return 0;
        }
        long overtime = Math.max(0, workedMinutes - contract.weeklyMinutes());
        if (overtime < PAYROLL_BLOCK_MINUTES) {
            return 0;
        }
        return overtime / PAYROLL_BLOCK_MINUTES * PAYROLL_BLOCK_MINUTES;
    }
}
