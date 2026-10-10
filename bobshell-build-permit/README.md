# The Build Permit Office

Standalone Java/JBang companion for "IBM Bob Hooks in Java: The Build Permit
Office". Bob follows filing clues, repairs a stamp wheel, verifies the
current inputs, and obtains `permit.json`. The supplied `game/` is the starting
project. Human-operated launchers create a separate disposable `workspace/`.

## Requirements

Tested with Bobshell 2.0.5 (commit `2dc180906`), JBang 0.138.0, Temurin JDK 25,
Maven Wrapper 3.9.11, and Jackson Databind 2.21.5. Sources target Java 21. JUnit
Jupiter 5.13.4, Compiler Plugin 3.14.1, and Surefire 3.5.4 are pinned in the POM.
The walkthrough targets macOS/Linux and requires JDK 21+, JBang, Bobshell (whose
installation requirements include Node.js 24+), and
network access for dependency downloads and inference. No Python or containers.

- [Install Bobshell and configure authentication](https://bob.ibm.com/docs/shell/getting-started/install-and-setup).
- [Install JBang](https://www.jbang.dev/documentation/jbang/latest/installation.html).
- [IBM Bob trial](https://bob.ibm.com/trial).

## Initialize and check without Bob

Make a disposable copy of this companion and run all launcher commands from its
root. Do not run the launchers from the preview repository root.

```sh
jbang scripts/Hunt.java init
jbang scripts/CheckHunt.java
```

Initialization copies `game/` to `workspace/`, assigns a fresh run ID, warms JBang,
and compiles the Maven tests without executing them. The starter's backward-wrap
test is **intentionally failing**. `CheckHunt.java` uses a temporary copy, runs real
Maven tests before and after repair, checks handler responses and issuance rules,
then removes its temporary directory. It does not change your current game.

The launchers create `workspace/` and write run results to `evidence/`. Maven
creates `target/` directories. These generated files are ignored by Git and are
not part of the starting project.

Review [game/.bob/settings.json](game/.bob/settings.json) and
[the handler](game/.bob/hooks/OfficeHook.java). The game disables global hooks and
allows selected native reads/edits and the office command prefix. The pre-tool
handler narrows those commands to three exact forms and restricts native edits to
two files. These are convenience settings for this disposable game.

## Let Bob play

Create an Inference API key according to IBM's setup instructions. Keep the
downloaded JSON outside both the companion and `workspace/`. Pass its filename:

```sh
export BOB_HUNT_KEY_FILE="/absolute/path/to/your-bob-inference-key.json"
jbang scripts/Hunt.java play --key-file "$BOB_HUNT_KEY_FILE"
jbang scripts/Hunt.java status
jbang scripts/Hunt.java events
```

The launcher reads only the `apikey` field and supplies it through `BOB_API_KEY` to
its Bob child process. It also accepts an existing `BOB_API_KEY` environment
variable if `--key-file` is omitted. Never paste the credential into a prompt.

The mission prompt requests one premature issuance attempt, then leaves discovery
and repair to Bob. A successful run has `permitValid: true` in the status and
`workspace/target/case-report.json`, as well as a current
`workspace/permit.json`. Model wording and the sequence of reads may vary.

The launcher limits each Bob session to 35 turns and a cost limit of 2, disables
MCP and subagents, and trusts only this game workspace. A limit or interruption
can leave a partially solved case. Resume its task ID or use the solution below.

## Inspect the checkpoints

From the companion root:

```sh
jbang scripts/Hunt.java play --key-file "$BOB_HUNT_KEY_FILE" --prompt reception-block
jbang scripts/Hunt.java play --key-file "$BOB_HUNT_KEY_FILE" --prompt reception-allow
```

The first intentionally returns exit 1 because its prompt lacks the case prefix;
its ignored raw response contains `Prompt blocked by hook`. The second reports
the case from startup context with zero tool calls. The case-number prefix checks
syntax; it is not authentication.

The `feedback` prompt appends a source comment using a native edit and leaves the
bug in place. Run it on a fresh game if you want to inspect failed JUnit feedback
before repair:

```sh
jbang scripts/Hunt.java play --key-file "$BOB_HUNT_KEY_FILE" --prompt feedback
```

## Expire an old stamp

After successful verification:

```sh
jbang scripts/Hunt.java stale
jbang scripts/Hunt.java play --key-file "$BOB_HUNT_KEY_FILE" --prompt stale
jbang scripts/Hunt.java play --key-file "$BOB_HUNT_KEY_FILE" --prompt recover
```

`stale` adds a harmless source comment. The old permit remains on disk but becomes
invalid. The attempted issuance should be refused with:

```text
Your stamp refers to an earlier version of this application. Verify again.
```

The recovery prompt verifies the current files and issues a fresh permit. To keep
these turns in the same conversation, add `--resume TASK_ID` to each `play` command.
The task ID is printed in the launcher's receipt.

## Interactive TUI and compaction

```sh
jbang scripts/Hunt.java chat --key-file "$BOB_HUNT_KEY_FILE" --resume TASK_ID
```

Use `/hooks` to inspect registrations. Ordinary user prompts must start with
`CASE: BUILD-001`; `/compact` is a local slash command. After manual compaction,
inspect `.office/checkpoint.json`, `.office/compaction.json`, and the event log.
The expected sequence is `PreCompact`, `PostCompact`, then
`SessionStart` providing current case facts again. A checkpoint never overrides
current files or revalidates an expired stamp.

The article's screenshot was captured from the real Bobshell TUI inside IBM Bob's
integrated terminal. Run receipts are generated locally and are not included in
this companion.


## Verification and boundaries

```sh
jbang scripts/CheckBob.java --key-file "$BOB_HUNT_KEY_FILE"
```

This resets the generated workspace, checks reception and failed-test feedback,
runs two fresh hunts, then tests stale refusal and recovery in a resumed session.
It writes summary receipts in `evidence/`. Full Bob responses are credential
redacted and ignored by Git; review them locally before sharing any logs.

The pre-tool handler and the issuer both check the key, run ID, verification
result, and current fingerprint. Fingerprints cover source, tests, application,
filing records, build files, and office controls, with checks before and after the
test process. Generated files are excluded.

This is a cooperative game scoped to selected tool forms. Hooks run with your
user permissions. Ordinary hook failures can fail open; native edit coverage does
not cover arbitrary filesystem access. The stamps are local, unsigned JSON. The
game is not adversarial isolation, release authorization, or a substitute for CI
against the final source tree. 
