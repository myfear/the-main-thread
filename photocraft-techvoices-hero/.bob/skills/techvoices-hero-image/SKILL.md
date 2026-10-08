---
name: techvoices-hero-image
description: Create an editable, topic-specific 1200 by 630 IBM Tech Voices article hero in PhotoCraft using the supplied brand base. Use for a new hero or a revision of a hero produced by this skill.
user-invocable: true
---

# Tech Voices hero images

Use the connected `photocraft` MCP server to build the artwork in the live PhotoCraft window. Read [the brand reference](references/brand.md) and [the command notes](references/photocraft.md). For requested typography, also read [the type and placement notes](references/typography.md). Resolve bundled assets relative to this skill's folder; resolve automation file paths relative to the configured PhotoCraft read/write root.

## Brief

Accept a title plus a short summary, or read the local article draft the user identifies. Infer one visual idea from the article's main mechanism or consequence. Ask for a title and one sentence about the topic only when neither the request nor the supplied draft gives enough information. Describe the chosen idea briefly, then construct it. Use topic-specific geometry, or an editorial type composition when the user requests typography. The image should communicate something beyond the brand palette.

For this tutorial, MAKE IT REPEATABLE with offset copies of the dominant word expresses a repeatable publishing workflow. BRIEF, BUILD, PUBLISH and SKILL TO CANVAS are alternative typographic ideas. Flowing bands becoming ordered layers are a geometric alternative. For an article about memory as storage, use separate stored records or stacked compartments with a path into and out of them. These are examples, not a fixed template for every topic.

## Create or revise

1. Inspect the live session and discover the needed engine commands with narrowly filtered `command_list` calls such as `shape.create`, `shape.edit`, `layer.moveTo`, and `layer.setProps`. For type, discover `type.create`, `type.info`, `type.edit`, and `type.fonts` as needed. Do not invent command IDs or parameters. If the bridge is unavailable, report that the desktop app must be started with this workspace's setup script.
2. For a new hero, `doc_open` the bundled `assets/hero-base.pcraft`. Inspect the document and find layers by name rather than assuming their IDs. Confirm that the intended document is active; use the session's document indices and `document.activate` if necessary. Choose a new output basename under `output/`; check the actual directory listing or file existence before saving, since searches may skip ignored files. If a basename exists, choose the next numeric suffix and report it. An explicit revision opens the identified output document instead of the base.
3. Create artwork within `x=0..1200, y=0..540`. Keep important forms at least 32 pixels from the side edges. Put new Shape and Type layers inside the `Topic artwork` group using `layer.moveTo` with `position: "into"`. Make a balanced composition with a clear focal form and space between substantial elements. Gradients and opacity may use the brand palette. For kinetic typography, use a short message, deliberate scale, and separate editable echo layers; measure ink bounds before placement and check readability at 300 pixels wide. Avoid visual clutter, tiny symbols, and unrelated technical icons. Keep each substantial form editable and name its layer by function.
4. Preserve `Paper`, `Brand footer`, and `Brand — IBM Tech Voice`. Leave the bottom 90 pixels white. Use only the supplied Tech Voice wordmark; do not add other logos. Do not redraw, recolor, distort, filter, hide, or move the wordmark. Do not add a headline unless the user requests one.
5. Inspect the layer tree and use `shape.info` or `type.info` where placement checks help. Fit the canvas with `ui_set` and obtain a `ui_screenshot`. Assess the actual preview, including text legibility, overlaps, footer visibility, and whether the picture fits the brief. Correct concrete problems before exporting. A tool's completion message is not a visual check.
6. Save to explicit new paths using `doc_save` for `output/<basename>.pcraft` and `doc_export` for `output/<basename>.png`. Never save back to `assets/hero-base.pcraft`. Check and report export warnings. Reopen directly with `doc_open` (the live bridge does not support `doc_close`) and verify the 1200 by 630 dimensions, editable Shape or Type layers, and footer wordmark. Retain the reopened document as the visible result.
7. Write `output/<basename>-alt.txt` with one or two sentences, at most 60 words, describing the actual topic illustration and the footer wordmark. In the final response link the PNG and native document and state the visual idea in one sentence. Report a concrete validation gap if a check could not be performed.

If an asset is missing, identify the missing file instead of generating a replacement logo. If an operation fails after earlier edits succeeded, inspect the document before retrying; a batch does not roll back successful earlier steps. Use small batches that stop on error. A later revision keeps the existing footer and original output unless the user explicitly requests replacement.
