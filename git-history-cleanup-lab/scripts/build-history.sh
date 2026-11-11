#!/bin/sh
set -eu

root="$(cd "$(dirname "$0")/.." && pwd -P)"
target="${1:-$root/work/payroll-lab}"

# Only direct children of the real work directory are replaceable.
case "$target" in
  /*) ;;
  *) target="$PWD/$target" ;;
esac
case "$target" in
  "$root"/work/*) name="${target#"$root/work/"}" ;;
  *)
    echo "Refusing to replace $target" >&2
    echo "Use a direct child of $root/work." >&2
    exit 1
    ;;
esac
case "$name" in
  ''|.*|*[!a-zA-Z0-9._-]*)
    echo "Use a simple directory name under $root/work." >&2
    exit 1
    ;;
esac
if [ -L "$root/work" ] || [ -L "$target" ]; then
  echo "Refusing to replace a symlinked work directory or target." >&2
  exit 1
fi
if [ -e "$target" ] && { [ -L "$target/.git" ] ||
    [ ! -f "$target/.git/main-thread-lab" ] ||
    ! grep -qx 'git-history-cleanup-lab' "$target/.git/main-thread-lab"; }; then
  echo "Refusing to replace a directory not created by this lab: $target" >&2
  exit 1
fi
command -v git >/dev/null
command -v rsync >/dev/null

rm -rf "$target"
mkdir -p "$target"
git -C "$target" init -q --initial-branch=main --object-format=sha1 --template=
git -C "$target" config user.name "The Main Thread Lab"
git -C "$target" config user.email "lab@example.invalid"
git -C "$target" config commit.gpgSign false
git -C "$target" config core.hooksPath /dev/null
git -C "$target" config core.autocrlf false
mkdir -p "$target/.git/info"
echo 'git-history-cleanup-lab' > "$target/.git/main-thread-lab"

commit_snapshot() {
  snapshot="$1"
  rsync -a --delete --exclude=.git "$snapshot/files/" "$target/"
  date="$(cat "$snapshot/date.txt")"
  git -C "$target" add -A
  GIT_AUTHOR_NAME="The Main Thread Lab" GIT_AUTHOR_EMAIL="lab@example.invalid" \
    GIT_COMMITTER_NAME="The Main Thread Lab" GIT_COMMITTER_EMAIL="lab@example.invalid" \
    GIT_AUTHOR_DATE="$date" GIT_COMMITTER_DATE="$date" \
    git -C "$target" commit -q -F "$snapshot/message.txt"
}

for snapshot in "$root"/history/0[1-6]-*; do
  commit_snapshot "$snapshot"
done

git -C "$target" switch -q -c cleanup/naive
commit_snapshot "$root/history/07-cleanup"

git -C "$target" switch -q -c solution/from-cleanup
commit_snapshot "$root/history/08-fix-guard"
commit_snapshot "$root/history/09-pay-501"

git -C "$target" switch -q main
