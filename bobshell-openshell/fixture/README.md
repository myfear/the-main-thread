# Bounded retry delay

`retryDelay(attempt, baseMs, capMs)` should return an exponentially increasing
delay that never exceeds `capMs`. Assume non-negative integer attempts and
positive, finite delays. The current implementation ignores the cap.

Run the tests with `node --test retry-delay.test.mjs`. Fix the implementation
without editing the tests or installing dependencies.
