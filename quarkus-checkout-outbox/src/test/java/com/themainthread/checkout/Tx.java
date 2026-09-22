package com.themainthread.checkout;

import java.util.function.Supplier;

import io.quarkus.narayana.jta.QuarkusTransaction;

final class Tx {

    private Tx() {
    }

    static <T> T call(Supplier<T> work) {
        return QuarkusTransaction.requiringNew().call(work::get);
    }

    static void run(Runnable work) {
        QuarkusTransaction.requiringNew().run(work);
    }
}
