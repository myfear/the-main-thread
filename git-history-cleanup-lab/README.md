# Read the Git history before you clean up the code

Investigate a passing cleanup that removes a live payroll guard and applies a future
policy early. Use Git history, a deleted regression check, and two missing cases to
separate those changes from a safe simplification.

Companion for The Main Thread article
[Review AI Code Cleanup with Git History](https://www.the-main-thread.com/p/git-history-ai-code-review).
The history, incidents, and tickets are fictional teaching fixtures.

## Prerequisites

- JDK 25 on `PATH`, including `java` and `javac`.
- Git 2.30 or newer.
- A POSIX shell, `rsync`, and the usual Unix command-line tools (macOS, Linux, or WSL).
- Optional: [IBM Bob Shell](https://bob.ibm.com/download?bob=shell) and `BOB_API_KEY`
  for the agent comparison. The command options were checked with Bob Shell 2.0.9.

The Java code uses only the JDK. There is no Maven, Gradle, database, or container.
The check scripts compile into temporary directories and remove compiled output when
they exit. Console output from the Java check programs is the lab's test report.

## Build the exercise

From this companion directory:

```sh
./scripts/assemble.sh
./scripts/check.sh work/payroll-lab
```

The companion ships snapshots and scripts as ordinary files, without a nested Git
repository or submodule. The assembler creates the exercise repository locally
under the ignored `work/payroll-lab` directory, on `main`. Commit the companion
files to the parent repository; keep generated `work/` content out of that commit.
The suite ends with:

```text
SUMMARY 6 checks, 0 failures
```

Running `assemble.sh` again **replaces that generated repository, including your
exercise edits and commits**. Copy out anything you want to keep first. The builder
rejects traversal paths, symlinked targets, and existing directories without its lab
marker. It sets its own initial branch, author identity, and commit settings.

The generated branches are:

- `main`: six commits covering the guard, rounding, validation, a deleted check,
  the January requirement, and the emergency caller.
- `cleanup/naive`: the baseline plus the deliberately incorrect cleanup.
- `solution/from-cleanup`: the proposal followed by a behavior-preserving repair
  and a separate January policy change.

The assembler copies `scripts/check.sh`, `scripts/boundary.sh`, and `checks/` into the
generated repository and excludes them through `.git/info/exclude`. They remain
available across branch switches without becoming part of the scenario's history.

## 1. Inspect the proposal

```sh
cd work/payroll-lab
git switch -c review/cleanup cleanup/naive
git diff main...review/cleanup
./scripts/check.sh
```

The suite still reports six checks and zero failures. `CLEANUP.md` is the proposal
being reviewed; its conclusions are deliberately wrong.

`Contract.includesOvertime()` means the contract's fixed allowance covers overtime,
so it receives no separate payment. Standard contracts currently receive overtime in
complete 15-minute blocks. `workedMinutes` always means the total for the payroll
week, including on the emergency-dispatch path. The lab assumes realistic,
nonnegative inputs and does not implement payroll exports or persistence.

The proposal removes the flat-rate guard, a threshold early return, and block
rounding. Investigate each removal separately.

## 2. Recover the evidence

```sh
git log main --oneline --graph --decorate
git log main -L :paidOvertimeMinutes:src/main/java/com/themainthread/payroll/OvertimeRules.java
git blame main -L 7,10 -- src/main/java/com/themainthread/payroll/OvertimeRules.java
git log main -S 'includesOvertime' --oneline -- src/main/java/com/themainthread/payroll/OvertimeRules.java
git log main -S 'PAYROLL_BLOCK_MINUTES' --oneline -- src/main/java/com/themainthread/payroll/OvertimeRules.java
git log main --diff-filter=D -p -- '*PayrollExportChecks.java'
```

The `.gitattributes` entry `*.java diff=java` selects Java function detection for the
named-method history command. These commands start at `main` so the proposal and
solution cannot obscure the origins of the rules.

Read `EmergencyDispatchService.java` and `docs/payroll-2027.md`, then record:

- **Flat-rate guard:** INC-412 introduced it in 2019. Emergency dispatch bypasses
  the scheduling validator, and PAY-501 preserves the rule. Keep it and add coverage.
- **Block rounding:** PAY-88 introduced it in 2021. PAY-501 replaces both the format
  and payment rule in January 2027. Preserve it for November; remove it for rollout.
- **Threshold early return:** integer division already gives zero for nonnegative
  overtime below 15. Remove it while retaining rounding; this preserves behavior.

The deleted `PayrollExportChecks` class directly tests the calculation. Its name
and removal with export-related checks do not make it an end-to-end integration test.

## 3. Run the missing cases

Still on `review/cleanup`:

```sh
./scripts/boundary.sh
```

Expected output and exit status 1:

```text
FAIL flat-rate emergency shift: expected 0, got 480
PASS 10 minutes overtime: 10
PASS 45 hours: 300
```

Compare with `main`:

```sh
git switch main
./scripts/boundary.sh
git switch review/cleanup
```

Expected output and exit status 1:

```text
PASS flat-rate emergency shift: 0
FAIL 10 minutes overtime: expected 10, got 0
PASS 45 hours: 300
```

The boundary script expects January behavior for the ten-minute case. Its failure on
`main` shows an unimplemented future requirement, rather than a November regression.
The flat-rate failure on the proposal is a regression under either policy.

## 4. Preserve the current behavior

In `src/main/java/com/themainthread/payroll/OvertimeRules.java`, restore the constant,
flat-rate guard, and rounding. Leave the redundant threshold return removed:

```java
package com.themainthread.payroll;

public final class OvertimeRules {

    static final long PAYROLL_BLOCK_MINUTES = 15;

    public long paidOvertimeMinutes(long workedMinutes, Contract contract) {
        if (contract.includesOvertime()) {
            // INC-412: flat-rate contracts are paid through their allowance.
            // Emergency dispatch bypasses ShiftAssignmentValidator.
            return 0;
        }
        long overtime = Math.max(0, workedMinutes - contract.weeklyMinutes());
        return overtime / PAYROLL_BLOCK_MINUTES * PAYROLL_BLOCK_MINUTES;
    }
}
```

In `src/test/java/com/themainthread/payroll/Checks.java`, add this call after the
existing emergency-dispatch check and before the summary:

```java
check("emergency dispatch, flat-rate contract, 48h", 0,
        new EmergencyDispatchService(new OvertimeRules())
                .payForEmergencyShift(flatRate, 2880));
```

Remove the superseded proposal, verify the seven-check suite, and commit:

```sh
git rm CLEANUP.md
./scripts/check.sh
git add src/main/java/com/themainthread/payroll/OvertimeRules.java \
        src/test/java/com/themainthread/payroll/Checks.java
git commit -m "Preserve payroll behavior after cleanup"
```

You can stop here for a behavior-preserving cleanup. The boundary script still
reports zero rather than ten for the future-policy case. Block rounding remains.

## 5. Prepare the January policy change

Replace `OvertimeRules.java` with the following version:

```java
package com.themainthread.payroll;

public final class OvertimeRules {

    public long paidOvertimeMinutes(long workedMinutes, Contract contract) {
        if (contract.includesOvertime()) {
            // INC-412: flat-rate contracts are paid through their allowance.
            // Emergency dispatch bypasses ShiftAssignmentValidator.
            return 0;
        }
        // PAY-501: the January 2027 export accepts minutes.
        return Math.max(0, workedMinutes - contract.weeklyMinutes());
    }
}
```

Add this check immediately after the flat-rate dispatch check:

```java
check("standard contract, 10 minutes overtime", 10,
        new OvertimeRules().paidOvertimeMinutes(2410, standard));
```

Read and copy the reference decision record, then verify and commit the policy change:

```sh
mkdir -p docs/decisions
git show solution/from-cleanup:docs/decisions/0007-overtime-pay.md \
  > docs/decisions/0007-overtime-pay.md
cat docs/decisions/0007-overtime-pay.md
./scripts/check.sh
./scripts/boundary.sh
git add src/main/java/com/themainthread/payroll/OvertimeRules.java \
        src/test/java/com/themainthread/payroll/Checks.java \
        docs/decisions/0007-overtime-pay.md
git commit -m "Apply per-minute overtime for January rollout (PAY-501)"
```

Expect eight checks with zero failures, plus all three boundary cases passing.
The lab has no date-based policy selector: this version applies January behavior
whenever it runs. Keep the preceding commit for November payroll.

## 6. Compare the completed trees

```sh
git diff review/cleanup solution/from-cleanup -- src docs
git show solution/from-cleanup~1
git show solution/from-cleanup
```

The first command compares the two tips directly; it should be empty if you followed
the edits exactly. The other commands show the separate repair and policy commits.

## 7. Run the optional Bob comparison

Return to the companion root with `cd ../..`. Obtain a Bob API key through your
account and provide it as `BOB_API_KEY` in the environment. Do not put it in this
repository. Then run:

```sh
./scripts/run-bob.sh work/payroll-lab full
./scripts/run-bob.sh work/payroll-lab docs-only
./scripts/run-bob.sh work/payroll-lab source-only
```

Every condition starts from committed `main`, regardless of your current branch or
uncommitted edits. All three receive the same source, README, attributes, check
script, and cleanup prompt. `full` receives the six baseline commits and `docs/`;
`docs-only` receives `docs/` without `.git`; `source-only` receives neither.

Workspaces live in the OS temporary directory, outside any parent Git repository.
The full clone has no remote and no cleanup or solution branches. This controls the
supplied evidence; it is not a filesystem or network sandbox. The script uses Bob's
non-interactive mode, which pre-approves tool calls, accepts the license, and trusts
the disposable workspace.

Defaults are 15 turns and `--max-cost 0.60`. IBM documents the cost option in
Bobcoins, not dollars. Override with `BOB_MAX_TURNS` and `BOB_MAX_COST` if needed.
See [IBM's non-interactive session documentation](https://bob.ibm.com/docs/shell/getting-started/start-bobshell-non-interactive)
for API-key requirements and option semantics.

Each run saves `result.json`, `stderr.log`, `source.diff`, `checks.log`, `prompt.txt`,
and `metadata.txt` under `work/bob-runs/`. Metadata records the baseline commit,
workspace path, CLI version, limits, and process/check exit statuses. Workspaces are
retained for inspection. Remove the recorded temporary workspace when you no longer
need it; reassembling the lab does not remove Bob runs.

Inspect the JSON events as well as the script's exit code. Bob 2.0.9 can emit a
turn-limit error followed by a success result and exit 0. The wrapper flags error
events as failures while keeping the original process status in metadata. A passing
suite alone does not establish task completion or correct reasoning. Compare removals and cited
facts, and retain capped or failed runs. This small authored example is an exercise,
not a model benchmark.

## Script reference

- `assemble.sh`: rebuild the marked exercise repository and install its check helpers.
- `build-history.sh [target]`: build the nine snapshots under a direct child of `work/`.
- `check.sh [repo]`: compile the lab classes with `--release 25` and run `Checks`;
  also run `PayrollExportChecks` when inspecting a historical revision containing it.
- `boundary.sh [repo]`: run two missing cases and the 45-hour control case.
- `run-bob.sh [repo] [full|docs-only|source-only]`: run and capture a fresh comparison.

The supplied snapshots intentionally contain incomplete tests and a broken proposal.
Use `solution/from-cleanup` for the January reference behavior.
