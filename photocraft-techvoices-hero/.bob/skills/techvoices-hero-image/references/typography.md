# Editable kinetic typography

PhotoCraft v0.2.0 supports native Type layers. The tested examples use installed
IBM Plex Sans and IBM Plex Sans Condensed at weight 700. These fonts are a
tutorial choice, not a requirement stated by the supplied kit. The footer wordmark
is supplied pixel artwork and must never be recreated as text.

## Fonts and commands

Use narrow `command_list` filters for `type.fonts`, `type.create`, `type.info`,
`type.edit`, and `type.setStyle` when needed. `type.fonts` lists installed
families. Check the requested family and bold face before construction. If a
required font is missing, identify it and direct the user to
[IBM Plex releases](https://github.com/IBM/plex/releases); do not silently
substitute a font or rasterize the type.

Create type with `type.create`. Its `x,y` point is the baseline, `size` is in
points, and `tracking` is in thousandths of an em. For example:

```json
{
  "id": "type.create",
  "params": {
    "x": 0, "y": 0,
    "text": "MAKE IT",
    "font": "IBM Plex Sans Condensed",
    "weight": 700, "size": 160,
    "color": "#000000", "tracking": -15,
    "name": "MAKE IT"
  }
}
```

The result's `layer` is the current document's new Type layer ID. Resolve it
from the result. `type.edit` accepts `layer`, replacement `text`, or a
six-number affine `transform: [a,b,c,d,e,f]`. Use `type.setStyle` for type style
changes. Discover its parameter schema before using it.

## Measured placement

`type.info` reports ink bounds as **[x0,y0,x1,y1]**. `doc_inspect` layer bounds
use **[x,y,width,height]**. Point type created at `(0,0)` can have a negative
top coordinate because the letters sit above the baseline. Measure before
placement rather than treating the point as the upper-left corner.

For uniform fitting into a target `[left,top,width,height]`:

1. Create the Type layer at `(0,0)` with its intended family and style.
2. Get its ink bounds from `type.info`.
3. Rotate all four bounds corners through the requested angle θ. Compute the
   resulting `minX`, `maxX`, `minY`, and `maxY`.
4. Set `s = min(width/(maxX-minX), height/(maxY-minY))`.
5. Apply `type.edit` with this absolute transform:

```text
[s*cos(θ), s*sin(θ), -s*sin(θ), s*cos(θ),
 left-s*minX, top-s*minY]
```

Angles in the calculation are radians. A small negative angle makes the word
rise from left to right in the document's downward-positive y coordinates.
Keep the resulting ink above y=540 and important letters inside the side margins.
Use the supplied measured recipes when reproducing the bundled compositions.
Recipes assume the same font metrics; measure again if the fonts differ.

## Motion echoes and hierarchy

Use separate Type layers with identical text, font, size, and scale for motion
echoes. Offset those layers in one deliberate direction. Use Blue 30 echoes
behind Blue 80 or white type, or Blue 80 echoes behind Blue 30 on black.
`layer.setProps` opacity is in the range 0..1. Keep the primary word opaque and
legible. Create and move layers into `Topic artwork` in back-to-front order;
later layers sit above earlier ones.

One clear message is enough. MAKE IT REPEATABLE uses repetition to express
reuse. BRIEF, BUILD, PUBLISH creates a reading path. SKILL TO CANVAS resolves
moving upper type into a level lower word. These are static poster treatments;
the PNG is not an animation.

Inspect Type layers, preview the actual canvas, save to new native and PNG
paths, reopen with `doc_open`, and confirm the words remain editable. PNG
export flattens the layers; the `.pcraft` preserves them. Preserve the three fixed
layers and the entire white footer during both creation and refinement.
