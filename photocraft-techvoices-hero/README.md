# Tech Voices hero images with IBM Bob and PhotoCraft

This article follows a brand kit into a reusable Bob skill that creates a 1200 × 630 PNG, an editable PhotoCraft document, and alt text. This companion contains the complete skill, its assets, four example images with their native documents, and setup scripts for macOS.

## Start PhotoCraft and connect Bob

Use IBM Bob with MCP enabled on macOS. Install **IBM Plex Sans Bold** and **IBM Plex Sans Condensed Bold** from [IBM Plex releases](https://github.com/IBM/plex/releases) for the editable typography. The supplied footer wordmark is an image and needs no fonts. The examples used PhotoCraft v0.2.0, Bob IDE 2.2.1, and BobShell 2.0.5.

Open this `photocraft-techvoices-hero` folder as its own project in Bob, so it finds the project-local `.bob` directory. Run these commands from a terminal in this folder:

```sh
sh scripts/install-macos.sh
sh scripts/configure-macos.sh
sh scripts/start-photocraft.sh
```

The installer downloads the official PhotoCraft v0.2.0 app and CLI into `.runtime` and checks their signatures. The configuration script uses macOS's built-in `plutil` to write `.bob/mcp.json` with absolute paths for this folder. It preserves other server entries and replaces the `photocraft` entry. You can inspect the portable configuration in [.bob/mcp.example.json](.bob/mcp.example.json).

The startup script opens PhotoCraft with its authenticated control bridge on port 7878. PhotoCraft creates a private token file on first start. The app and bridge share that file, and their read/write roots cover this project. Keep the terminal running while Bob edits the image.

In Bob Settings → MCP, confirm that **photocraft** is connected. In Settings → Skills, confirm that **techvoices-hero-image** appears with Workspace scope. Use Agent mode. If you added the configuration while Bob was open, restart the server or reload the window.

Start a conversation with:

```text
/techvoices-hero-image
Create a hero for “Teach IBM Bob to Create Tech Voices Hero Images with PhotoCraft”.
Summary: turn a brand kit into a reusable Bob skill and create editable article artwork through PhotoCraft MCP.
Use a kinetic typography composition: black MAKE IT above blue REPEATABLE on white, with two pale blue copies trailing down and right. Add the smaller line ONE BRIEF. MANY IMAGES.
Use editable IBM Plex Type layers, preserve the footer, and preview at card size.
Save a new result under output/make-it-repeatable, with PNG, native document, and alt text under 60 words.
```

The supplied configuration leaves automatic tool approval empty. Approve the editing operations as Bob requests them. You should see the PhotoCraft canvas change, then receive paths to the PNG, `.pcraft` document, and alt text. Use a new basename for each image to keep the existing examples.

## Open the examples

- **Make it repeatable:** [PNG](output/kinetic-prototypes/a-repeatable.png), [editable document](output/kinetic-prototypes/a-repeatable.pcraft), [alt text](output/kinetic-prototypes/a-repeatable-alt.txt), [recipe](output/kinetic-prototypes/a-repeatable-render-recipe.json).
- **Brief, build, publish:** [PNG](output/kinetic-prototypes/b-publishing.png), [editable document](output/kinetic-prototypes/b-publishing.pcraft), [alt text](output/kinetic-prototypes/b-publishing-alt.txt), [recipe](output/kinetic-prototypes/b-publishing-render-recipe.json).
- **Skill to canvas:** [PNG](output/kinetic-prototypes/c-skill-canvas-v2.png), [editable document](output/kinetic-prototypes/c-skill-canvas-v2.pcraft), [alt text](output/kinetic-prototypes/c-skill-canvas-v2-alt.txt), [recipe](output/kinetic-prototypes/c-skill-canvas-v2-render-recipe.json).
- **Agent memory is a storage system:** [PNG](output/agent-memory-storage-v1.png), [editable document](output/agent-memory-storage-v1.pcraft), [alt text](output/agent-memory-storage-v1-alt.txt).

The article contains the full prompts for all four compositions. These prompts let Bob choose placement, so results can vary. The JSON recipes contain the fonts, text, transforms, and layer properties used in the three saved typography examples. You can ask Bob to read a recipe and apply those values through PhotoCraft, starting from a fresh base and saving under a new name. Use the same installed fonts for the same text measurements.

## Keep the skill and base together

The complete skill lives in [.bob/skills/techvoices-hero-image](.bob/skills/techvoices-hero-image/SKILL.md). Its references explain the [brand choices](.bob/skills/techvoices-hero-image/references/brand.md), [PhotoCraft commands](.bob/skills/techvoices-hero-image/references/photocraft.md), and [editable typography](.bob/skills/techvoices-hero-image/references/typography.md).

Copy that entire skill folder into another Bob project's `.bob/skills/` to reuse it. Keep its assets and references together, and configure PhotoCraft for that project. The app's read/write roots must include the assets and output folder. MCP file paths are relative to those roots.

The bundled `assets/hero-base.pcraft` is ready to open. Its three fixed layers are `Paper`, `Brand footer`, and `Brand — IBM Tech Voice`. Add each article's shapes and text to `Topic artwork` and save under a new path.

If you want to prepare your own base in PhotoCraft, create a white 1200 × 630 RGB document and reserve the bottom 90 pixels for a white footer. Copy the supplied cropped Tech Voice wordmark into its own layer and scale it uniformly to 260 pixels wide, with its left edge at x=908. Center it vertically at y=585 and leave the rest of the footer white. Lock the paper, footer, and wordmark layers, then add an empty `Topic artwork` group. Keep the prepared base under a separate filename while adjusting it.

The cropped wordmark and prepared base are included in the skill's `assets` folder. Use only that wordmark in the footer. New shapes and text use black, white, Blue 80, and Blue 30, as recorded in the brand reference. The footer dimensions and IBM Plex fonts are composition choices for this example.

## Check an export

Install ImageMagick through [Homebrew](https://formulae.brew.sh/formula/imagemagick) if needed:

```sh
brew install imagemagick
magick identify -format '%f: %wx%h, %[colorspace], %z-bit\n' \
  output/kinetic-prototypes/a-repeatable.png
```

Expect `a-repeatable.png: 1200x630, sRGB, 8-bit`. Use your new image's filename to check its export. `magick identify -verbose` prints metadata as well. Look at the image at article-card size and reopen its native document in PhotoCraft to check the lettering and layers.


## Troubleshooting and stopping

- **Connection refused:** start PhotoCraft before Bob's MCP bridge. Both use port 7878. If you change the port, update the startup script and MCP registration together.
- **Authentication failure:** use the same token-file path for the desktop app and bridge.
- **File operation rejected:** start PhotoCraft with this project's read/write roots, use relative forward-slash paths, and create output directories before saving.
- **Missing font:** install the two IBM Plex bold faces before creating typography.
- **Preview contains the application window:** `ui_screenshot` captures PhotoCraft's window; `doc_export` produces the article PNG.
- **Export warnings:** PNG flattens the layers. Keep the native `.pcraft` file for later edits and inspect any other warnings.

Close PhotoCraft or interrupt its terminal when finished, then stop the server in Bob's MCP settings. Runtime binaries, the private token, and machine-specific MCP configuration are ignored by Git.

Primary documentation: [PhotoCraft control bridge](https://github.com/storytold/photocraft/blob/v0.2.0/docs/control-protocol.md), [Bob MCP configuration](https://bob.ibm.com/docs/ide/configuration/mcp/mcp-in-bob), and [Bob skills](https://bob.ibm.com/docs/ide/features/skills).
