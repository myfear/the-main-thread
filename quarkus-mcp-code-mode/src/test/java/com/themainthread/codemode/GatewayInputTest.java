package com.themainthread.codemode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class GatewayInputTest {
    @Test
    void gatesStartupAndPassesCompleteLinesAtTheByteLimit() throws Exception {
        var input = input("éé\nx\n", 4);
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var pending = threads.submit(input::readAllBytes);
            Thread.sleep(30);
            assertFalse(pending.isDone());
            input.open();
            assertEquals("éé\nx\n", new String(pending.get(1, TimeUnit.SECONDS), StandardCharsets.UTF_8));
        }
    }

    @Test
    void closesOnOversizedOrIncompleteFramesWithoutDeliveringPartialJson() throws Exception {
        for (String source : new String[] { "12345\n{}\n", "1234" }) {
            var input = input(source, 4);
            input.open();
            assertTrue(input.readAllBytes().length == 0);
            assertEquals(-1, input.read());
        }
    }

    private GatewayInput input(String text, int maximum) {
        return new GatewayInput(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), maximum);
    }
}
