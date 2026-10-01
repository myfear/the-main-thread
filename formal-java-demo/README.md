# Formal Java verification demo

```java
if (available.get() > 0) {
    available.decrementAndGet();
}
```

With one item and two callers, both callers can read `1` before either
decrements. The final stock is `-1`. `AtomicInteger` makes each operation atomic,
but the compound check-then-act protocol is not atomic.

This project checks one rule: **inventory must never become negative**.

| Tool | What it checks here |
| --- | --- |
| JUnit 5 | Selected executions, including a forced race and two concurrent reservations |
| Americium | Up to 1,000 generated inputs in `0..10_000` for the pure Java transition |
| TLA+ / TLC | All reachable states of a model with two workers and one item |
| Lean 4 | A proof that the pure transition preserves non-negative stock for every non-negative integer |

## Prerequisites

Use JDK 21, Bash, `curl`, and either `sha256sum` or `shasum`. Set `JAVA_HOME` to
your JDK 21 installation. Maven Enforcer rejects other Java major versions.
On Windows, run the full verification in WSL with these tools installed there.

Install Lean through [elan, Lean's toolchain manager](https://lean-lang.org/install/),
then make its commands available in your shell:

```bash
export PATH="$HOME/.elan/bin:$PATH"
```

From `formal-java-demo/`, prepare the pinned Lean toolchain:

```bash
cd formal/lean
elan toolchain install leanprover/lean4:v4.34.1
lake build
cd ../..
```

The wrapper downloads Maven. The formal scripts download the official TLA+ JAR
only when it is missing, and check its pinned SHA-256 on every run. Initial
setup needs network access for Maven dependencies, TLA+, and Lean. Tool binaries
and generated build files are excluded from Git.

## Run the checks

Run these commands from `formal-java-demo/`:

```bash
./mvnw test
./scripts/run-broken-tla.sh
./scripts/verify-formal.sh
./mvnw verify
```

- `./mvnw test` runs JUnit and Americium. It neither downloads TLC nor requires Lean.
- `./scripts/run-broken-tla.sh` deliberately fails. Expect a `NoOversell`
  counterexample ending at `stock = -1` and a nonzero exit status.
- `./scripts/verify-formal.sh` checks the fixed TLA+ model, then runs `lake build`.
- `./mvnw verify` compiles Java, runs all tests, packages the class, and runs
  both formal checks through `exec-maven-plugin` in the `verify` phase. A failed
  check fails Maven. No profile is needed. If `lake` is missing, the script
  reports the missing prerequisite and exits with an error.

The broken TLA+ model is excluded from the normal build. The broken Java test
passes by asserting the deliberately incorrect outcome.

## Read the example

[Inventory.java](src/main/java/example/Inventory.java) is the only production
class. It rejects negative initial stock and uses a compare-and-set loop. A
successful CAS commits the decrement only if the observed stock is still
current; otherwise, the loop reads the stock again.

[BrokenInventoryTest](src/test/java/example/BrokenInventoryTest.java) uses a
latch after the stock check to force both callers to observe the last item.
Neither can decrement until both checks have passed. No timing or sleeps are
involved. [InventoryTest](src/test/java/example/InventoryTest.java) releases two
callers together and checks that exactly one succeeds. Their scheduling order
is unspecified; the expected outcome does not depend on it. This test does
not force every CAS retry path.

[InventoryPropertyTest](src/test/java/example/InventoryPropertyTest.java) uses
Americium's Java `Trials` API and JUnit 5 `@TrialsTest` integration. It checks
non-negativity, non-increase, and the exact conditional decrement. Sequential
JUnit checks also cover zero, one, and `Integer.MAX_VALUE`.

[InventoryBroken.tla](formal/tla/InventoryBroken.tla) splits `Check` and
`Decrement`. [InventoryFixed.tla](formal/tla/InventoryFixed.tla) combines the
decision and update into one `Reserve` action. A terminal self-loop allows the
finished workers to remain idle without TLC reporting a deadlock. The models
check safety only. The two modeled workers are independent of TLC's own
`-workers 1` checker-thread setting.

## Why the proof and counterexample agree

[Inventory.lean](formal/lean/Inventory.lean) proves that, given non-negative
stock, `reserve` returns non-negative stock. The transition checks the current
value and conditionally decrements it as one mathematical operation.

The broken Java protocol separates the check from the decrement. A second
caller can run between them, so the decrement can act on a different value
from the one that passed the check. Lean's proof and TLC's counterexample are
both correct: they describe different compositions of operations.

Lean uses unbounded `Int`, while Java uses a signed 32-bit `int`. For this
transition, subtracting one only when the input is positive cannot overflow;
zero remains zero. The proof needs only Lean's bundled `omega` tactic, with no
Mathlib or external SMT solver. Lake treats warnings as errors, so replacing
the proof with `sorry` also fails the build.

## What remains unproved

Passing the TLA+ model does not prove that `Inventory.java` implements the TLA+
specification. Passing the Lean proof does not prove that `Inventory.java`
implements the Lean definition.

The Java tests and property tests provide a small bridge between the formal
definitions and implementation. They do not constitute a formal refinement
proof. The fixed model abstracts a reservation as one atomic action; it does
not model the CAS loop or the Java memory model. TLC exhausts only the configured
one-item, two-worker state space. Lean's theorem is universal over its input,
but proves only the local arithmetic property.
