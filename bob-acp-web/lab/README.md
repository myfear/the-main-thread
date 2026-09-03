# Retry budget lab

`RetryBudget.canRetry(failedAttempts, maxAttempts)` answers whether a caller may make another attempt.

The contract is deliberately small:

- `maxAttempts` includes the initial call. A value of `3` permits an initial call and two retries.
- `failedAttempts` is the number of calls that have already failed.
- Return `true` only while another attempt remains.
- Reject a negative `failedAttempts` or a `maxAttempts` below `1` with `IllegalArgumentException`.

Run `./verify` from this directory. The implementation is intentionally wrong at the start of the tutorial.
