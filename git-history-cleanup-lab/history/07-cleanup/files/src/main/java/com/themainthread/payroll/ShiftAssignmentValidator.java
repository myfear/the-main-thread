package com.themainthread.payroll;

public final class ShiftAssignmentValidator {

    public void validate(Contract contract, boolean overtimeEligible) {
        if (contract.includesOvertime() && overtimeEligible) {
            throw new IllegalArgumentException(
                    "flat-rate contracts cannot take overtime-eligible shifts");
        }
    }
}
