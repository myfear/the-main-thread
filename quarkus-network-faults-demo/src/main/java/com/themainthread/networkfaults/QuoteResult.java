package com.themainthread.networkfaults;

record QuoteResult(CarrierQuote quote, String failure) {

    static QuoteResult available(CarrierQuote quote) {
        return new QuoteResult(quote, null);
    }

    static QuoteResult unavailable(Throwable cause) {
        return new QuoteResult(null, cause.getClass().getSimpleName());
    }

    boolean available() {
        return quote != null;
    }
}
