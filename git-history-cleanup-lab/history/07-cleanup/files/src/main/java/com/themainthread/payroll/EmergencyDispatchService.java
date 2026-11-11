package com.themainthread.payroll;

public final class EmergencyDispatchService {

    private final OvertimeRules rules;

    public EmergencyDispatchService(OvertimeRules rules) {
        this.rules = rules;
    }

    /**
     * Models emergency dispatch bypassing ShiftAssignmentValidator for an already
     * started shift. workedMinutes is the total for the payroll week; this example
     * does not persist shifts.
     */
    public long payForEmergencyShift(Contract contract, long workedMinutes) {
        return rules.paidOvertimeMinutes(workedMinutes, contract);
    }
}
