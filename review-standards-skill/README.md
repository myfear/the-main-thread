# Review Standards Skill Eval

This sample project compares three `review-standards` cases with Promptfoo:

- no skill loaded
- stale skill that finds both auth issues but ignores what the user asked for
- improved skill that finds both issues and stays inside the request

Run it from this directory:

```bash
npx promptfoo@latest eval -c promptfooconfig.yaml
npx promptfoo@latest view --port 15500
```

The local script provider always gives the same result. It lets the dashboard
show the eval shape without requiring a live model key. For a real coding-agent
eval, replace the `exec:` providers with `openai:codex-sdk`,
`anthropic:claude-agent-sdk`, or another agent provider and keep the same tests
and checks.

Expected result:

- 3 passing results
- 3 failing results
- `improved-review-standards` passes both tasks

Promptfoo returns a non-zero exit code because the weak cases fail. That is
expected for this sample project.
