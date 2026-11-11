package com.themainthread.payroll;

public final class Checks {

    private static int total;
    private static int failures;

    public static void main(String[] args) {
        Contract standard = new Contract("C-100", false, 2400);

        check("standard contract, 45h", 300,
                new OvertimeRules().paidOvertimeMinutes(2700, standard));
        check("standard contract, 40h", 0,
                new OvertimeRules().paidOvertimeMinutes(2400, standard));
        check("standard contract, 39h", 0,
                new OvertimeRules().paidOvertimeMinutes(2340, standard));

        System.out.printf("SUMMARY %d checks, %d failures%n", total, failures);
        if (failures > 0) {
            System.exit(1);
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
