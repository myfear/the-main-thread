#!/bin/sh
set -eu
app_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
demo_dir="$app_dir/.local/demo-app"
if [ ! -d "$demo_dir" ]; then
    mkdir -p "$demo_dir"
    cp "$app_dir/demo-app/pom.xml" "$app_dir/demo-app/mvnw" "$app_dir/demo-app/mvnw.cmd" "$demo_dir/"
    cp -R "$app_dir/demo-app/src" "$app_dir/demo-app/.mvn" "$demo_dir/"
    # The upstream server requires wrappers to be tracked before executing them.
    git -C "$demo_dir" init --quiet
    git -C "$demo_dir" add pom.xml mvnw mvnw.cmd src .mvn
fi
printf '%s\n' "$demo_dir"
