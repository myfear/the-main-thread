#!/usr/bin/env bash
set -euo pipefail

# Only operate on the disposable publishing checkouts, never a developer checkout.
if [[ "${GITHUB_ACTIONS:-}" != true ]]; then
  echo 'This publisher runs only inside GitHub Actions.' >&2
  exit 1
fi

: "${GITHUB_WORKSPACE:?}" "${GITHUB_SHA:?}" "${GITHUB_RUN_NUMBER:?}" "${GITHUB_RUN_ATTEMPT:?}" "${GITHUB_REPOSITORY:?}"
main_repo="$GITHUB_WORKSPACE/main-repo"
badges_repo="$GITHUB_WORKSPACE/badges-branch"
tool="$GITHUB_WORKSPACE/.github/scripts/ProjectCatalog.java"

configure_author() {
  git -C "$1" config user.name 'Markus Eisele'
  git -C "$1" config user.email 'markus@eisele.net'
}

refresh() {
  git -C "$1" fetch --no-tags origin "$2"
  git -C "$1" reset --hard FETCH_HEAD
}

configure_author "$main_repo"
configure_author "$badges_repo"

# Both loops run under the publishing job's shared concurrency lock. A rejected
# push is rebuilt on fresh branch contents, including per-project run provenance.
for attempt in 1 2 3; do
  refresh "$main_repo" main
  refresh "$badges_repo" badges
  jbang "$tool" aggregate --root "$main_repo" \
    --matrix "$GITHUB_WORKSPACE/plan/matrix.json" \
    --results "$GITHUB_WORKSPACE/results" --badges "$badges_repo" \
    --source-sha "$GITHUB_SHA" --run-number "$GITHUB_RUN_NUMBER" --run-attempt "$GITHUB_RUN_ATTEMPT"
  git -C "$badges_repo" add -- '*.json'
  if git -C "$badges_repo" diff --cached --quiet; then break; fi
  git -C "$badges_repo" commit -m 'Update build badges [skip ci]'
  if git -C "$badges_repo" push origin HEAD:badges; then break; fi
  if [[ "$attempt" == 3 ]]; then echo 'Badge push failed after three attempts.' >&2; exit 1; fi
done

for attempt in 1 2 3; do
  refresh "$main_repo" main
  refresh "$badges_repo" badges
  jbang "$tool" render --root "$main_repo" --badges "$badges_repo" --repository "$GITHUB_REPOSITORY" --write
  if git -C "$main_repo" diff --quiet -- README.md; then break; fi
  git -C "$main_repo" add -- README.md
  git -C "$main_repo" commit -m 'docs: Update companion catalog'
  if git -C "$main_repo" push origin HEAD:main; then break; fi
  if [[ "$attempt" == 3 ]]; then echo 'README push failed after three attempts.' >&2; exit 1; fi
done
