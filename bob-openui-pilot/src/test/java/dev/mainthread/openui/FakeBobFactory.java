package dev.mainthread.openui;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.Mock;
import jakarta.inject.Singleton;

@Mock
@Singleton
class FakeBobFactory extends BobFactory {
    static volatile CountDownLatch waiting = new CountDownLatch(1);

    FakeBobFactory(BobConfig config, ObjectMapper mapper) {
        super(config, mapper);
    }

    @Override
    BobConnection open() {
        return new BobConnection() {
            private final CountDownLatch closed = new CountDownLatch(1);

            @Override
            public String initialize() {
                return "test-agent";
            }

            @Override
            public String prompt(String instructions, Consumer<String> text) throws Exception {
                if (instructions.endsWith("provider-failure")) {
                    throw new IllegalStateException("provider-private-detail-never-expose");
                }
                if (instructions.endsWith("wait-until-cancelled")) {
                    waiting.countDown();
                    closed.await(15, TimeUnit.SECONDS);
                    return "cancelled";
                }
                text.accept("root = Report(\"Test\", ");
                text.accept("\"Supplied facts only.\", [Missing(\"Still needed\", [\"Date\"])])");
                return "end_turn";
            }

            @Override
            public void close() {
                closed.countDown();
            }
        };
    }
}
