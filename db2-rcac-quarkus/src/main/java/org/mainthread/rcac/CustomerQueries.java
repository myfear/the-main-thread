package org.mainthread.rcac;

public final class CustomerQueries {
    private static final String PROJECTION = "CUSTOMER_ID, TENANT_ID, DISPLAY_NAME, EMAIL, CREDIT_LIMIT, STATUS";
    public static final String LIST = "SELECT " + PROJECTION + " FROM APP.CUSTOMERS ORDER BY CUSTOMER_ID";
    public static final String FIND = "SELECT " + PROJECTION + " FROM APP.CUSTOMERS WHERE CUSTOMER_ID = ?";

    private CustomerQueries() {
    }
}
