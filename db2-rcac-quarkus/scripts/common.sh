#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
DB2_IMAGE=icr.io/db2_community/db2:12.1.5.0
DB2_CONTAINER=db2-rcac
DB2_VOLUME=db2-rcac-data

die() { printf '%s\n' "$*" >&2; exit 1; }

load_env() {
    local line key value
    [[ -f "$PROJECT_ROOT/.env.db2" ]] || die 'Create .env.db2 first. See .env.db2.example or scripts/configure.sh.'
    while IFS= read -r line || [[ -n "$line" ]]; do
        [[ -z "$line" || "$line" == \#* ]] && continue
        [[ "$line" == *=* ]] || die 'Invalid .env.db2 entry; use KEY=value without shell syntax.'
        key=${line%%=*}
        value=${line#*=}
        case "$key" in
            LICENSE|DB2INST1_PASSWORD|DB2_A_PASSWORD|DB2_B_PASSWORD|DB2_A_PII_PASSWORD|DB2_UNMAPPED_PASSWORD|HTTP_ALICE_PASSWORD|HTTP_BOB_PASSWORD|HTTP_AUDITOR_PASSWORD|HTTP_UNMAPPED_PASSWORD|DB2_PORT)
                printf -v "$key" '%s' "$value" ;;
            *) die "Unsupported .env.db2 key: $key" ;;
        esac
    done < "$PROJECT_ROOT/.env.db2"
    [[ "${LICENSE:-}" == accept ]] || die 'Review the image license and explicitly set LICENSE=accept.'
    for key in DB2INST1_PASSWORD DB2_A_PASSWORD DB2_B_PASSWORD DB2_A_PII_PASSWORD DB2_UNMAPPED_PASSWORD HTTP_ALICE_PASSWORD HTTP_BOB_PASSWORD HTTP_AUDITOR_PASSWORD HTTP_UNMAPPED_PASSWORD; do
        value=${!key:-}
        [[ ${#value} -ge 12 && "$value" != replace-with-* ]] || die "Set a local password of at least 12 characters for $key."
        [[ "$value" != *$'\r'* && "$value" != *:* ]] || die "Do not use colon or carriage return in $key."
    done
    DB2_PORT=${DB2_PORT:-50000}
    [[ "$DB2_PORT" =~ ^[0-9]+$ ]] && (( DB2_PORT > 0 && DB2_PORT < 65536 )) || die 'Invalid DB2_PORT.'
}

select_database() {
    RCAC_DATABASE=${1:-RCACLAB}
    case "$RCAC_DATABASE" in RCACLAB|RCACIT) ;; *) die 'Database must be RCACLAB or RCACIT.' ;; esac
}

db2_command() {
    local sql=$1
    podman exec "$DB2_CONTAINER" su - db2inst1 -c "db2 connect to $RCAC_DATABASE >/dev/null && db2 -x \"$sql\"; rcac_exit_code=\$?; db2 connect reset >/dev/null; exit \$rcac_exit_code"
}

app_environment() {
    env DB2_JDBC_URL="jdbc:db2://localhost:$DB2_PORT/$RCAC_DATABASE" \
        DB2_LAB_JDBC_URL="jdbc:db2://localhost:$DB2_PORT/RCACLAB" \
        DB2_A_PASSWORD="$DB2_A_PASSWORD" DB2_B_PASSWORD="$DB2_B_PASSWORD" \
        DB2_A_PII_PASSWORD="$DB2_A_PII_PASSWORD" DB2_UNMAPPED_PASSWORD="$DB2_UNMAPPED_PASSWORD" \
        HTTP_ALICE_PASSWORD="$HTTP_ALICE_PASSWORD" HTTP_BOB_PASSWORD="$HTTP_BOB_PASSWORD" \
        HTTP_AUDITOR_PASSWORD="$HTTP_AUDITOR_PASSWORD" HTTP_UNMAPPED_PASSWORD="$HTTP_UNMAPPED_PASSWORD" \
        "$@"
}
