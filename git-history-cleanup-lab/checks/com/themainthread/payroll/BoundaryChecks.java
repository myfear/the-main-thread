package com.themainthread.payroll;

public final class BoundaryChecks {

    private static int failures;

    public static void main(String[] args) {
        Contract standard = new Contract("C-100", false, 2400);
        Contract flatRate = new Contract("C-200", true, 2400);

        check("flat-rate emergency shift", 0,
                new EmergencyDispatchService(new OvertimeRules())
                        .payForEmergencyShift(flatRate, 2880));
        check("10 minutes overtime", 10,
                new OvertimeRules().paidOvertimeMinutes(2410, standard));
        check("45 hours", 300,
                new OvertimeRules().paidOvertimeMinutes(2700, standard));

        if (failures > 0) {
            System.exit(1);
        }
    }

    private static void check(String name, long expected, long actual) {
        if (expected == actual) {
            System.out.printf("PASS %s: %d%n", name, actual);
        } else {
            failures++;
            System.out.printf("FAIL %s: expected %d, got %d%n", name, expected, actual);
        }
    }
}
