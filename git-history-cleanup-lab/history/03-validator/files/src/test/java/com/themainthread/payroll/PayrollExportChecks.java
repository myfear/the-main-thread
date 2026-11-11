package com.themainthread.payroll;

public final class PayrollExportChecks {

    public static void main(String[] args) {
        Contract flatRate = new Contract("C-200", true, 2400);
        long paid = new OvertimeRules().paidOvertimeMinutes(2880, flatRate);
        if (paid != 0) {
            throw new AssertionError("flat-rate contract paid " + paid + " overtime minutes");
        }
        System.out.println("PASS flat-rate contract, 48h: 0");
    }
}
