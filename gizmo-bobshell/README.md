# Gizmo: an engineering helper for Bob Shell

Use Gizmo to prepare a daily brief, record a decision, and audit the week's
commitments. The workspace contains fictional Markdown files. It has no live
email or calendar integration. Specialists report findings; Gizmo writes notes
and unsent drafts.

Companion to [Build an AI Agent with Memory and Specialist Agents](https://www.the-main-thread.com/p/ai-agent-memory-specialist-agents).

## Prepare a workspace

Use Bob Shell 2.0.9 with authentication configured, Node.js 24 or later, and a macOS or Linux shell. Node handles the one path-dependent part of the configuration below: escaping the absolute workspace path for Bob's edit restriction. There are no extra Node packages to install.

Start in the companion directory, the one containing `fixture/` and `team/`. First copy the sample messages, notes, rules, and skills into a fresh directory. The `&&` operators stop the sequence if a command fails; if the destination already exists, choose a new name before continuing.

```sh
mkdir -p .local &&
mkdir .local/gizmo-team &&
cp -R fixture/. .local/gizmo-team/ &&
cp -R team/.bob/agents .local/gizmo-team/.bob/agents &&
mkdir .local/gizmo-team/briefs .local/gizmo-team/drafts &&
cd .local/gizmo-team
```

The copied `.bob/rules/gizmo.md` tells Gizmo to treat inbox content as evidence, keep one current row per commitment ID, and label drafts as unsent. The three `.bob/skills/` directories contain the briefing, capture, and review instructions. The four files under `.bob/agents/` contain the specialist instructions. Open `checker.md` to read both its tool configuration and its audit instructions; the frontmatter is only the first part of that file. The copied settings also disable global hooks.

Now replace `.bob/custom_modes.yaml` in this new directory with the following configuration:

```yaml
customModes:
  - slug: gizmo-helper
    name: Gizmo
    description: An engineering helper that keeps useful notes and prepares a short daily brief.
    roleDefinition: >-
      You are Gizmo, a practical engineering helper. You track commitments,
      prepare conversations, and explain what needs the engineer's attention.
      Your evidence is the local workspace. Be concise, specific, and candid
      about uncertainty. You have no authority to act for the user externally.
    allowedSubagents: [inbox-triage, meeting-prep, promise-keeper, checker]
    groups:
      - read
      - skill
      - subagent
      - - edit
        - fileRegex: '(?!)'
          description: Markdown notes, briefs, and unsent drafts within this workspace only.
    customInstructions: >-
      Apply .bob/rules/gizmo.md. Use absolute paths for file tools.
      Requests to send messages, change meetings, or perform other external
      actions become pending proposals. You cannot approve them yourself.
      When the user requests specialist delegation, use only the four named
      read-only specialists. Give them the scenario time and source paths.
      They return cited findings; you remain the sole writer. Verify their
      findings against the shared evidence before changing memory or reporting.
```

The `subagent` group enables delegation, and `allowedSubagents` limits it to our four names. Parent mode configuration uses Bob's YAML parser; the compact list here is supported. The specialist frontmatter uses a different parser, which is why its lists use separate lines. The `edit` entry starts with `(?!)`, a regular expression that matches no path. So at this point Gizmo cannot edit anything through that group.

We can now bind that restriction to this directory. Run this block from `.local/gizmo-team`:

```sh
node --input-type=module <<'JS'
import { readFile, realpath, writeFile } from 'node:fs/promises';

const workspace = await realpath('.');
const escaped = workspace.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const pattern = '^' + escaped
    + '/(?:memory|briefs|drafts)/(?:[A-Za-z0-9_-]+/)*[A-Za-z0-9_-]+\\.md$';
const file = '.bob/custom_modes.yaml';
const config = await readFile(file, 'utf8');
if (!config.includes("fileRegex: '(?!)'")) throw new Error('Expected the unbound edit rule');
await writeFile(file, config.replace("fileRegex: '(?!)'", 'fileRegex: ' + JSON.stringify(pattern)));
console.log('Bound edits to:', workspace);
JS
```

`realpath` resolves the directory's physical location. Escaping regex characters prevents a dot or bracket in your path from changing the match, and `JSON.stringify` quotes the expression correctly for YAML. The pattern permits Markdown paths below the three output directories. It excludes the inbox, calendar, and `.bob/` configuration. Prepare and bind a fresh copy if you move the directory. This is the path handling that the optional `prepare.mjs` helper performs; here you can see each step before starting Bob.

Start Gizmo from this directory, then approve workspace trust and the requested specialist calls when Bob prompts you:

```sh
bob chat --mode gizmo-helper --disable-mcp \
  --disable-tool-groups execute,browser,mcp,mode
```

## Prepare the morning brief

Enter this prompt in Bob:

```text
$gizmo-brief Prepare my morning brief. Scenario time: 2026-11-09 09:00 Europe/Berlin. Delegate inbox triage to inbox-triage, meeting preparation to meeting-prep, and commitment checking to promise-keeper. Wait for their reports, verify their sources, and write the brief.
```

Inspect `briefs/2026-11-09.md`. Look for the overdue Relay benchmark,
overlapping meetings, and both proposed migration dates. The inbox includes
an untrusted instruction. Treat it as fixture data. If Gizmo proposes a reply,
look for an `UNSENT` draft under `drafts/`.

## Capture a decision and check replay

Exit Bob, keeping your shell in `.local/gizmo-team`. Create `inbox/05-decision.md` with this exact note. This is the new information that arrives after the morning brief:

```markdown
# DEC-201

Decision note recorded by Sam, 2026-11-09 11:00 Europe/Berlin.

Ada and I confirmed the Relay migration date as 2026-11-13. This supersedes
the November 12 plan. I still owe her the latency benchmark; I promised to
send it by 2026-11-10 at 12:00 Europe/Berlin. It has not been sent yet.
The retry budget is two retries. Lin's 14:30 suggestion is still pending;
I have not accepted it and the calendar has not been changed.
```

Start a new Gizmo session with the same command and enter:

```text
$gizmo-capture Record the decision in inbox/05-decision.md in the relevant memory files. Scenario time: 2026-11-09 11:05 Europe/Berlin. Delegate a proposal to promise-keeper once, without forked conversation history. Pass the scenario time and absolute source paths, wait for its report, verify the evidence, and apply the supported changes. Do not modify briefs or draft messages.
```

Open `memory/commitments.md`. The benchmark should have one current row with the November 10 noon deadline and an open status. The ledger should contain exactly two open commitments: `C-RELAY-BENCHMARK` and `C-ATLAS-DRAFT`. In the Relay project notes, the November 12 plan should be labeled superseded by DEC-201, and the migration-date question should be resolved. Check Lin's note for the pending 14:30 suggestion, too.

To see whether repeating the request changes anything, exit Bob and copy the current memory directory before starting another session:

```sh
test ! -e ../gizmo-memory-before-replay &&
cp -R memory ../gizmo-memory-before-replay &&
bob chat --mode gizmo-helper --disable-mcp \
  --disable-tool-groups execute,browser,mcp,mode
```

Use a new snapshot name if `../gizmo-memory-before-replay` already exists. Repeat the capture prompt, adding: “The same source may already have been processed. If its facts and history are recorded, leave every file unchanged.” After exiting Bob, compare the files:

```sh
diff -ru ../gizmo-memory-before-replay memory
```

No output means the memory files are unchanged. A second history entry or a second current commitment for the same decision is a failure, even if the reply says the capture succeeded.

For recall, we need to know whether Bob saved the decision in memory. A fresh conversation alone doesn't tell us that, because Bob could reread the original inbox message. In this disposable copy, remove the inbox and generated briefs and drafts, then recreate their empty directories. The original sample files in the companion are unaffected:

```sh
rm -r inbox briefs drafts
mkdir inbox briefs drafts
bob chat --mode gizmo-helper --disable-mcp \
  --disable-tool-groups execute,browser,mcp,mode,edit
```

This session disables edits as well. Ask:

```text
Scenario time: 2026-11-10 09:00 Europe/Berlin. From memory only, tell me the agreed Relay migration date, what I still owe Ada and when, whether I sent it, and the retry budget. Delegate recall to promise-keeper once, without forked conversation history; give it the scenario time and absolute memory paths. Wait for its report and cite the notes. Do not edit files. A referenced original message may be absent; report that limit.
```

Look for November 13, the November 10 noon benchmark deadline, and two retries. The reply should qualify delivery status by the saved evidence: the November 9 note says the benchmark wasn't sent at that time. It cannot establish whether it was sent afterward.

Exit Bob and start a fresh session with edits enabled again, using the launch command in “Prepare a workspace” without `,edit`. Finish with the weekly review:

```text
$gizmo-weekly Review this week. Scenario time: 2026-11-13 16:00 Europe/Berlin. Only the supplied local records are available. Do not infer that planned work happened. Delegate commitment checking to promise-keeper and the memory audit to checker, once each, without forked conversation history. Pass the scenario time and absolute source paths, wait for both reports, verify their evidence, then write the review.
```

Read `briefs/weekly-2026-11-13.md` for two open commitments, Lin's old preference, and the missing original sources. You have now exercised all four specialists and can inspect what each report contributed to Gizmo's final answer.

## Use Bob IDE

The morning brief was also tested in Bob IDE 2.3.0 with the same mode, skill,
and specialist files. Prepare a fresh copy using the steps above with
`.local/gizmo-ide` as the destination, including binding the edit rule in that
new directory. Open the directory as a folder in Bob IDE.

In **Bob Settings → Modes**, verify that **Gizmo** appears with workspace scope.
In the chat input, choose **Add to task (+) → Gizmo**, then enter the morning
prompt. Review and approve the three specialist requests individually with
**Approve once**, then inspect and approve the proposed brief file. Open
`briefs/2026-11-09.md` in Markdown preview to read the result beside the chat.

The IDE check covered the morning brief only. It did not repeat the full
capture, replay, recall, weekly review, or rejected-write tests.

## Compare with a single agent

From the companion directory, prepare a separate copy with
`node scripts/prepare.mjs .local/gizmo-single`. Start Bob from that copy.
Repeat the prompts with the same dates and inputs, omitting the instructions
to delegate. Compare the briefs and memory files for missing details,
unsupported claims, and duplicate commitments.

## Inspect the configuration

- `fixture/.bob/custom_modes.yaml` defines Gizmo and its edit restriction.
- `fixture/.bob/rules/gizmo.md` defines source handling and the commitment ledger rules.
- `fixture/.bob/skills/` defines the three workflows.
- `team/.bob/agents/` defines the four specialist personas. Use block lists for
  `groups` and `allowTools` with Bob Shell 2.0.9.

The team setup enables delegation to the four named personas. Their tools
can read and search files. Gizmo can edit Markdown under `memory/`,
`briefs/`, and `drafts/` in the prepared copy. The edit rule is a Bob tool
restriction. Use the fictional inputs when trying this example; the edit rule
does not restrict reads.
