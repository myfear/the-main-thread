#!/bin/sh
set -eu

root="$(cd "$(dirname "$0")/.." && pwd)"
repo="$root/work/payroll-lab"

"$root/scripts/build-history.sh" "$repo"

mkdir -p "$repo/scripts"
cp "$root/scripts/check.sh" "$root/scripts/boundary.sh" "$repo/scripts/"
chmod +x "$repo/scripts/"*.sh
cp -R "$root/checks" "$repo/checks"

touch "$repo/.git/info/exclude"
for entry in scripts/ checks/; do
  grep -qx "$entry" "$repo/.git/info/exclude" || echo "$entry" >> "$repo/.git/info/exclude"
done

echo "Repository ready at $repo"
echo "Next: cd work/payroll-lab && ./scripts/check.sh"
