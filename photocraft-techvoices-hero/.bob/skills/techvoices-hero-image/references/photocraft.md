# PhotoCraft command notes

Validated initial target: PhotoCraft v0.2.0, Bob IDE 2.2.1, macOS. The server is `photocraft-cli mcp --bridge 127.0.0.1:7878 --control-token-file <private-path>`. The desktop app owns file authority through its `--automation-read-root` and `--automation-write-root`. In this demo those roots are the project directory. Automation paths are forward-slash relative paths beneath those roots; their parent folders must already exist.

In the unmodified demo workspace the base is `.bob/skills/techvoices-hero-image/assets/hero-base.pcraft`. Do not hardcode the author's Downloads path. Keep credentials in the private runtime directory, outside the skill package.

## MCP tools and engine commands

Use `doc_open`, `doc_inspect`, `doc_save`, and `doc_export` for documents. `ui_inspect` includes the live session's document indices. In bridge mode, `doc_select` is unavailable: use `command_run` with `document.activate` and `{ "document": index }` if switching documents is needed.

Check the active document after opening, particularly when the same path is already open. `doc_close` is headless-only in v0.2.0; a live editing workflow can switch documents without closing tabs. Avoid concurrent document activation, opening, and editing calls because they share the active application state.

`command_list` accepts `filter`. `command_run` accepts `{ "id": "<command>", "params": {...} }`. `command_batch` accepts `{ "steps": [{ "id": "<command>", "params": {...} }], "stop_on_error": true }`. Stop-on-error stops later steps; earlier edits remain. Inspect before retrying. Use `doc_open`/`doc_save` rather than filesystem-bearing commands such as `file.placeEmbedded`, which v0.2.0 blocks in automation.

Example editable rectangle:

```json
{
  "id": "shape.create",
  "params": {
    "kind": "rect",
    "rect": [100, 100, 320, 180],
    "fill": "#002d9c",
    "name": "Stored record"
  }
}
```

The returned `layer` is its ID. Put it in the artwork group with `layer.moveTo`, `{ "layer": id, "target": groupId, "position": "into" }`.

Custom curves use `shape.create` with `kind: "path"`. A path contains `subpaths`; each has `closed`, an optional boolean operation, and `knots`. Corners are `[x,y]`. Curved knots are `{ "anchor": [x,y], "in": [x,y], "out": [x,y], "smooth": true }`; handles use document coordinates. `shape.edit` can replace paths or change fill, stroke, and position. Gradient fill supports `{"gradient":{"stops":[[0,"#002d9c"],[1,"#a6c8ff"]],"angle":0}}`. Ask `command_list` for the full parameters needed for the chosen form.

Use `ui_set` with `{ "fields": { "fit": true, "tool": "Move" } }` to present the canvas. `ui_screenshot` returns a PNG of the app window. `doc_render_preview` also returns a **window screenshot** in bridge mode; the exported PNG is the clean artwork. To save a window screenshot inside the workspace, `control_call` can invoke `ui.screenshot` with `{ "path": "evidence/<name>.png" }`.

Upstream documentation: https://github.com/storytold/photocraft/blob/v0.2.0/docs/control-protocol.md
