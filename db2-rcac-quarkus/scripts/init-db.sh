#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
load_env
select_database "${1:-RCACLAB}"
[[ $# -le 2 ]] || die 'Usage: init-db.sh [RCACLAB|RCACIT] [--reset-lab]'
reset=${2:-}
[[ -z "$reset" || "$reset" == --reset-lab ]] || die 'Only --reset-lab is accepted as the second argument.'

assert_public_privileges() {
    local broad_grants
    broad_grants=$(db2_command "SELECT COUNT(*) FROM SYSCAT.DBAUTH WHERE GRANTEE='PUBLIC' AND (CREATETABAUTH IN ('Y','G') OR BINDADDAUTH IN ('Y','G') OR IMPLSCHEMAAUTH IN ('Y','G'))")
    [[ "$(printf '%s' "$broad_grants" | tr -d '[:space:]')" == 0 ]] || die 'Unexpected broad PUBLIC database privileges. Use a fresh dedicated database or have its administrator review those grants.'
}

# These are ordinary OS users, never members of the instance administration group.
printf 'tena:%s\ntenb:%s\ntenapii:%s\ntennone:%s\n' \
    "$DB2_A_PASSWORD" "$DB2_B_PASSWORD" "$DB2_A_PII_PASSWORD" "$DB2_UNMAPPED_PASSWORD" |
    podman exec -i "$DB2_CONTAINER" bash -euc '
        getent group rcacusr >/dev/null || groupadd rcacusr
        while IFS=: read -r account password; do
            id "$account" >/dev/null 2>&1 || useradd -M -g rcacusr -s /bin/bash "$account"
            printf "%s:%s\n" "$account" "$password" | chpasswd
        done'

if [[ "$reset" == --reset-lab ]]; then
    printf 'Explicit reset: dropping the dedicated %s database and all its lab data.\n' "$RCAC_DATABASE"
    podman exec "$DB2_CONTAINER" su - db2inst1 -c "db2 deactivate db $RCAC_DATABASE && db2 drop db $RCAC_DATABASE"
fi
if connection_output=$(podman exec "$DB2_CONTAINER" su - db2inst1 -c "db2 connect to $RCAC_DATABASE" 2>&1); then
    table_count=$(db2_command "SELECT COUNT(*) FROM SYSCAT.TABLES WHERE TABSCHEMA='APP' AND TABNAME='CUSTOMERS'")
    table_count=$(printf '%s' "$table_count" | tr -d '[:space:]')
    if [[ "$table_count" == 1 ]]; then
        version=$(db2_command 'SELECT VERSION FROM LABSEC.INSTALLATION')
        [[ "$(printf '%s' "$version" | tr -d '[:space:]')" == 1 ]] || die 'Unexpected lab installation version.'
        assert_public_privileges
        printf '%s\n' 'Lab already initialized. No data or policies were replaced. Run scripts/verify-db2.sh.'
        exit 0
    fi
elif [[ "$connection_output" == *SQL1013N* || "$connection_output" == *SQL1031N* ]]; then
    podman exec "$DB2_CONTAINER" su - db2inst1 -c "db2 create database $RCAC_DATABASE using codeset UTF-8 territory US collate using identity"
else
    printf '%s\n' "$connection_output" >&2
    die 'Could not determine database state; refusing initialization.'
fi
assert_public_privileges

temporary_sql=$(mktemp)
trap 'rm -f "$temporary_sql"' EXIT
{
    printf 'CONNECT TO %s;\n' "$RCAC_DATABASE"
    cat "$PROJECT_ROOT/src/main/resources/sql/setup.sql"
    if [[ "$RCAC_DATABASE" == RCACIT ]]; then
        cat "$PROJECT_ROOT/src/main/resources/sql/write-probe-grants.sql"
    fi
    printf '\nINSERT INTO LABSEC.INSTALLATION VALUES (1);\nCOMMIT;\nCONNECT RESET;\n'
} > "$temporary_sql"
podman cp "$temporary_sql" "$DB2_CONTAINER:/tmp/rcac-setup.sql"
podman exec "$DB2_CONTAINER" chmod 644 /tmp/rcac-setup.sql
podman exec "$DB2_CONTAINER" su - db2inst1 -c 'db2 -svtf /tmp/rcac-setup.sql'
printf 'Initialized %s. Runtime passwords were never written into SQL.\n' "$RCAC_DATABASE"
