#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out=src/main/resources/pass-template
mkdir -p "$out"

# Source artwork contains no member text or barcode; Wallet renders those itself.
for scale in 1 2 3; do
  suffix="@${scale}x"
  [[ "$scale" == 1 ]] && suffix=""
  magick -background none -density "$((72 * scale))" design/icon.svg \
    -resize "$((38 * scale))x$((38 * scale))!" -strip "$out/icon${suffix}.png"
  magick design/wordmark-light-source.png -trim +repage \
    -resize "$((160 * scale))x$((50 * scale))" \
    -gravity center -background none -extent "$((160 * scale))x$((50 * scale))" \
    -strip "$out/logo${suffix}.png"
  if [[ "$scale" != 1 ]]; then
    magick design/wordmark-source.png -trim +repage \
      -resize "$((126 * scale))x$((30 * scale))" \
      -gravity center -background none -extent "$((126 * scale))x$((30 * scale))" \
      -strip "$out/primaryLogo${suffix}.png"
    magick design/artwork-source.png -resize "$((358 * scale))x$((448 * scale))!" \
      -strip "$out/artwork${suffix}.png"
  fi
done
