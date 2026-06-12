package dev.themainthread.invoicerecon.support;

import java.util.Optional;

import io.quarkiverse.mcp.server.Cancellation;

public final class TestCancellation implements Cancellation {

    private final int cancelAfterChecks;
    private int checks;

    public TestCancellation(int cancelAfterChecks) {
        this.cancelAfterChecks = cancelAfterChecks;
    }

    @Override
    public Result check() {
        checks++;
        if (checks > cancelAfterChecks) {
            return new Result(true, Optional.of("test cancellation"));
        }
        return new Result(false, Optional.empty());
    }
}
