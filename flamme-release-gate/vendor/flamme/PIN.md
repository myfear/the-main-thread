# Flamme source pin

The `runtime` and `deployment` modules are copied from:

- Repository: `https://github.com/AmadeusITGroup/flamme`
- Commit: `8afdaf6e8b59bc3b443750cf099971593ddb66c9`
- Snapshot version: `1.0.0-SNAPSHOT`
- License: MIT

The local copy has two deliberate changes:

- The two module POM parent declarations make the modules participate in this
  demo's Maven reactor.
- The `ToUpperComponentImpl` test fixture publishes its probe state before it
  releases the `CountDownLatch`. This removes a race in
  `PublishSubscribeLocalTest` that can otherwise fail in CI.

The production Java sources remain identical to the pinned upstream commit.
