#!/usr/bin/env bash
set -euo pipefail

module_dir=$(cd "$(dirname "$0")/.." && pwd)
source_ref=c5494f6e9702943702c79c4b0e6e92863e9ca77e
source_sha256=b536daa84eb284884360368099800f5c2248f9b2657861183765b0a1821d33b8
work_dir="$module_dir/target/endive-bootstrap"
archive="$work_dir/source.tar.gz"
mkdir -p "$work_dir"
curl -fsSL --retry 3 "https://codeload.github.com/quarkiverse/quarkus-endive/tar.gz/$source_ref" -o "$archive"
actual_sha256=$(shasum -a 256 "$archive" | cut -d ' ' -f 1)
if [ "$actual_sha256" != "$source_sha256" ]; then
    echo "Quarkus Endive source checksum mismatch" >&2
    exit 1
fi
source_dir=$(mktemp -d "$work_dir/source.XXXXXX")
tar -xzf "$archive" --strip-components=1 -C "$source_dir"
for pom in "$source_dir/pom.xml" "$source_dir/runtime/pom.xml" "$source_dir/deployment/pom.xml"; do
    sed -e 's#<version>999-SNAPSHOT</version>#<version>0.0.0-c5494f6e</version>#g' \
        -e 's#<endive.version>1.0.1</endive.version>#<endive.version>1.1.0</endive.version>#g' \
        "$pom" > "$pom.pinned"
    mv "$pom.pinned" "$pom"
done
"$module_dir/mvnw" -B -f "$source_dir/pom.xml" -pl runtime,deployment -am install \
    -DskipTests -Dendive.version=1.1.0 -Dquarkus.version=3.37.3 '-P!docs,!it'
