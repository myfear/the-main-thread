Trace how `@ConfigMapping` classes move from deployment scanning and generated mapping classes to synthetic bean registration and runtime creation.

Rules for this run:

- Use only shell commands and file reads (`execute_command`, `grep`, `read_file`).
- Do not use codebase-memory-mcp or any MCP graph tools.
- Do not load skills, spawn subagents, or create HTML artifacts.
- Start with `rg ConfigMappingBuildItem` and open files from the hits.
- Stop when you can name the main deployment steps, the arc deployment step, and the runtime creator.

Report back with:

1. Files you opened, in order (count them)
2. Which steps are build-time and which are runtime
3. What you are still unsure about
