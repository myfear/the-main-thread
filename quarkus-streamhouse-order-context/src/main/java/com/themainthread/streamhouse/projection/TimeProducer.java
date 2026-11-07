package com.themainthread.streamhouse.projection;

import java.time.Clock;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@Singleton
public class TimeProducer {
    @Produces
    @Singleton
    Clock clock() {
        return Clock.systemUTC();
    }
}
