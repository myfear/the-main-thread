package com.themainthread.payroll;

public final class OvertimeRules {

    public long paidOvertimeMinutes(long workedMinutes, Contract contract) {
        if (contract.includesOvertime()) {
            // INC-412: flat-rate contracts are paid through their allowance.
            // Emergency dispatch bypasses ShiftAssignmentValidator.
            return 0;
        }
        // PAY-501: the January 2027 export accepts minutes.
        return Math.max(0, workedMinutes - contract.weeklyMinutes());
    }
}
