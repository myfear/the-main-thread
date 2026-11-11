package com.themainthread.payroll;

public final class OvertimeRules {

    public long paidOvertimeMinutes(long workedMinutes, Contract contract) {
        if (contract.includesOvertime()) {
            return 0;
        }
        return Math.max(0, workedMinutes - contract.weeklyMinutes());
    }
}
