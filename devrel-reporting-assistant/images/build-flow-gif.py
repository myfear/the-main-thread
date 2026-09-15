"""Assemble the captured browser screenshots; requires Pillow."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
STEPS = [
    ('01-notes.png', 'Start with rough activity notes', 2800),
    ('02-question.png', 'Bob asks for the missing measurements', 3500),
    ('03-answers.png', 'Answer with a count and measurement date', 2500),
    ('04-ready.png', 'Review the report after Java validates it', 3500),
    ('05-saved.png', 'Save the report and download your JSON copy', 3500),
]
font_paths = [Path('/System/Library/Fonts/Supplemental/Arial.ttf'), Path('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf')]
font_path = next((p for p in font_paths if p.exists()), None)
font = ImageFont.truetype(str(font_path), 25) if font_path else ImageFont.load_default(size=25)
small = ImageFont.truetype(str(font_path), 16) if font_path else ImageFont.load_default(size=16)
frames = []
for index, (filename, label, _) in enumerate(STEPS):
    screenshot = Image.open(ROOT / 'flow' / filename).convert('RGB')
    assert screenshot.size == (1280, 720), screenshot.size
    frame = Image.new('RGB', (1280, 800), '#254e40')
    frame.paste(screenshot, (0, 80))
    draw = ImageDraw.Draw(frame)
    draw.text((28, 13), f'{index + 1:02d}  {label}', font=font, fill='white')
    draw.text((29, 48), 'FIELD NOTES  /  Quarkus + Bob', font=small, fill='#cddfd5')
    for step in range(len(STEPS)):
        x = 1150 + step * 22
        draw.ellipse((x, 30, x + 10, 40), fill='white' if step == index else '#6c9180')
    frames.append(frame)
# One palette avoids color flicker between otherwise stationary screenshots.
contact = Image.new('RGB', (1280, 800 * len(frames)))
for index, frame in enumerate(frames):
    contact.paste(frame, (0, index * 800))
palette = contact.quantize(colors=192, method=Image.Quantize.MEDIANCUT)
indexed = [frame.quantize(palette=palette, dither=Image.Dither.NONE) for frame in frames]
output = ROOT / 'field-notes-flow.gif'
indexed[0].save(output, save_all=True, append_images=indexed[1:], duration=[step[2] for step in STEPS], loop=0, optimize=True, disposal=1)
frames[3].save(ROOT / 'field-notes-flow-poster.png', optimize=True)
check = Image.open(output)
assert check.n_frames == len(STEPS)
assert check.size == (1280, 800)
print(f'{output.name}: {check.n_frames} frames, {check.size}, {output.stat().st_size / 1024:.0f} KiB')
# Review sheet is temporary and does not ship with the article.
contact.thumbnail((640, 2000))
contact.save('/private/tmp/field-notes-gif-contact.png')
