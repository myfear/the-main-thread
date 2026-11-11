package com.themainthread.payroll;

public final class Checks {

    private static int total;
    private static int failures;

    public static void main(String[] args) {
        Contract standard = new Contract("C-100", false, 2400);
        Contract flatRate = new Contract("C-200", true, 2400);

        check("standard contract, 45h", 300,
                new OvertimeRules().paidOvertimeMinutes(2700, standard));
        check("standard contract, 40h", 0,
                new OvertimeRules().paidOvertimeMinutes(2400, standard));
        check("standard contract, 39h", 0,
                new OvertimeRules().paidOvertimeMinutes(2340, standard));
        check("standard contract, 43h", 180,
                new OvertimeRules().paidOvertimeMinutes(2580, standard));
        checkThrows("flat-rate contract rejected for overtime shift",
                () -> new ShiftAssignmentValidator().validate(flatRate, true));
        check("emergency dispatch, standard contract, 45h", 300,
                new EmergencyDispatchService(new OvertimeRules())
                        .payForEmergencyShift(standard, 2700));
        check("emergency dispatch, flat-rate contract, 48h", 0,
                new EmergencyDispatchService(new OvertimeRules())
                        .payForEmergencyShift(flatRate, 2880));
        check("standard contract, 10 minutes overtime", 10,
                new OvertimeRules().paidOvertimeMinutes(2410, standard));

        System.out.printf("SUMMARY %d checks, %d failures%n", total, failures);
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static void checkThrows(String name, Runnable action) {
        total++;
        try {
            action.run();
            failures++;
            System.out.printf("FAIL %s: no exception%n", name);
        } catch (IllegalArgumentException expected) {
            System.out.printf("PASS %s: %s%n", name, expected.getMessage());
        }
    }

    private static void check(String name, long expected, long actual) {
        total++;
        if (expected == actual) {
            System.out.printf("PASS %s: %d%n", name, actual);
        } else {
            failures++;
            System.out.printf("FAIL %s: expected %d, got %d%n", name, expected, actual);
        }
    }
}
