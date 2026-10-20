#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
load_env
select_database "${1:-RCACLAB}"
[[ $# -le 1 ]] || die 'Usage: verify-db2.sh [RCACLAB|RCACIT]'
cd "$PROJECT_ROOT"
run_directory="$PROJECT_ROOT/eval/preflight-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$run_directory"
podman exec "$DB2_CONTAINER" su - db2inst1 -c 'db2level && db2licm -l' > "$run_directory/db2-version-license.txt"
podman image inspect "$DB2_IMAGE" --format '{{.Architecture}} {{.Digest}}' > "$run_directory/image.txt"
db2_command "SELECT TABSCHEMA,TABNAME,TABLEORG,CONTROL FROM SYSCAT.TABLES WHERE TABSCHEMA='APP' AND TABNAME='CUSTOMERS'" > "$run_directory/table-controls.txt"
db2_command "SELECT CONTROLSCHEMA,CONTROLNAME,CONTROLTYPE,ENABLE,VALID FROM SYSCAT.CONTROLS WHERE TABSCHEMA='APP' AND TABNAME='CUSTOMERS'" > "$run_directory/policies.txt"
app_environment ./mvnw test -Pdb2-integration -Dtest=Db2ReadSecurityTest \
    "-Drcac.evidence-dir=$run_directory"
printf 'Db2 preflight passed. Evidence: %s\n' "$run_directory"
