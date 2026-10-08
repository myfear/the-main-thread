# Tech Voices visual reference

The author supplied a Tech Voices brand kit. This skill carries its cropped wordmark and the following palette:

- Black 100: `#000000`
- White 0: `#ffffff`
- Blue 80: `#002d9c`
- Blue 30: `#a6c8ff`
- The wordmark reads **IBM Tech Voice**, singular; its latter two words are bold.
- Community names include **IBM Tech Voices** and **Tech Voices**.

The example banners and posts show layered curved bands, repeated slats, and light backgrounds. Use those as visual inspiration. The kit does not specify article-hero dimensions, a footer layout, a named font, or a complete hero template.

The tutorial's kinetic typography uses IBM Plex Sans and IBM Plex Sans Condensed in bold as a composition choice. It applies the kit's repetition to type. The phrase and its motion echoes belong to the topic artwork, above the footer. See `typography.md` for editable type placement; the supplied wordmark stays unchanged.

## This skill's design decisions

The canvas is 1200 by 630 pixels. The upper 540 pixels hold topic artwork; a white 90-pixel footer holds the Tech Voice wordmark on the right. The wordmark is approximately 260 pixels wide, ending at x=1168, and centered vertically at y=585. Leave the rest of the footer white. These dimensions are tutorial conventions, not additional rules attributed to the kit.

`tech-voice-wordmark.png` is cropped from the kit with PhotoCraft, including its original lettering. `hero-base.pcraft` contains the wordmark as a locked pixel layer, the footer as a locked shape layer, locked `Paper`, and an empty `Topic artwork` group. No font installation is required to reuse the wordmark.

Use only the supplied wordmark; do not add other logos. If the base needs rebuilding, place `tech-voice-wordmark.png` in its own layer using the dimensions above and preserve its proportions. Name that layer `Brand — IBM Tech Voice`, lock it with `Paper` and `Brand footer`, and keep new artwork inside `Topic artwork`.
