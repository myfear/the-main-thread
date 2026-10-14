#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
test "$(uname -s)" = Darwin || { echo 'Use the v0.5.0 release for your platform.' >&2; exit 1; }
mkdir -p .runtime
archive=gridcraft-cli-0.5.0-macos-universal.zip
curl -L --fail --silent --show-error \
  "https://github.com/storytold/gridcraft/releases/download/v0.5.0/$archive" \
  -o ".runtime/$archive"
expected=4559b42d96bafcbc4180cc7f09d5f428597ec19d447c3df2e4b458afcb4c04fc
actual=$(shasum -a 256 ".runtime/$archive" | cut -d ' ' -f 1)
test "$actual" = "$expected" || { echo 'Release archive checksum differs.' >&2; exit 1; }
unzip -o -q ".runtime/$archive" -d .runtime
.runtime/gridcraft-cli-0.5.0-macos-universal/gridcraft-cli version
