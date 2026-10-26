#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
out="${1:-runs/pilot-diagnostics-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$out"
for goal in dependencies audit; do
    report="$out/pilot-$goal.txt"
    if [ -e "$report" ]; then printf '%s\n' "Refusing to overwrite $report" >&2; exit 2; fi
    {
        printf '%s\n' 'Tool: eu.maveniverse.maven.plugins:pilot-plugin:0.5.0'
        printf 'Started UTC: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
        printf 'Command: ./mvnw -B -pl sample-app test-compile eu.maveniverse.maven.plugins:pilot-plugin:0.5.0:%s -Dpilot.action=report -Dpilot.showUndetermined=true -Dstyle.color=never\n' "$goal"
    } > "$report"
    status=0
    ./mvnw -B -pl sample-app test-compile "eu.maveniverse.maven.plugins:pilot-plugin:0.5.0:$goal" \
        -Dpilot.action=report -Dpilot.showUndetermined=true -Dstyle.color=never >> "$report" 2>&1 || status=$?
    printf '\nExit code: %s\nFinished UTC: %s\n' "$status" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$report"
done
printf '%s\n' "Captured optional diagnostics in $out. Audit output is not authoritative feed coverage."
