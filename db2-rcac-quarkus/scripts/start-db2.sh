#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
load_env
[[ $# -eq 0 || ( $# -eq 1 && "$1" == --allow-emulation ) ]] || die 'Usage: start-db2.sh [--allow-emulation]'
architecture=$(podman info --format '{{.Host.Arch}}')
platform=linux/amd64
if [[ "$architecture" != amd64 && "${1:-}" != --allow-emulation ]]; then
    die "The pinned Db2 amd64 image needs emulation on $architecture. Use --allow-emulation explicitly for functional tests."
fi
if podman container exists "$DB2_CONTAINER"; then
    actual_image=$(podman inspect --format '{{.Config.Image}}' "$DB2_CONTAINER")
    [[ "$actual_image" == "$DB2_IMAGE" ]] || die 'Existing db2-rcac container uses a different image.'
    podman start "$DB2_CONTAINER" >/dev/null
else
    mkdir -p "$PROJECT_ROOT/.local"
    umask 077
    container_env=$(mktemp "$PROJECT_ROOT/.local/container-env.XXXXXX")
    trap 'rm -f "$container_env"' EXIT
    printf 'LICENSE=accept\nDB2INSTANCE=db2inst1\nDB2INST1_PASSWORD=%s\nBLU=false\nTO_CREATE_SAMPLEDB=false\nREPODB=false\nPERSISTENT_HOME=true\n' "$DB2INST1_PASSWORD" > "$container_env"
    if ! podman volume exists "$DB2_VOLUME"; then
        podman volume create "$DB2_VOLUME" >/dev/null
    fi
    podman run --detach --name "$DB2_CONTAINER" --hostname db2-rcac \
        --platform "$platform" --privileged --cpus=4 --memory=8g --shm-size=1g \
        --publish "127.0.0.1:$DB2_PORT:50000" --env-file "$container_env" \
        --volume "$DB2_VOLUME:/database" "$DB2_IMAGE" >/dev/null
fi
printf 'Waiting for %s initialization (up to 15 minutes).\n' "$DB2_CONTAINER"
for ((attempt=0; attempt<180; attempt++)); do
    [[ "$(podman inspect --format '{{.State.Running}}' "$DB2_CONTAINER")" == true ]] || die 'Db2 container exited; inspect podman logs db2-rcac.'
    if podman logs "$DB2_CONTAINER" 2>&1 | grep 'Setup has completed' >/dev/null; then
        # Old setup logs survive a container restart. Check the current instance too.
        instance_ready=false
        if instance_output=$(podman exec "$DB2_CONTAINER" su - db2inst1 -c 'db2 list active databases' 2>&1); then
            instance_ready=true
        elif [[ "$instance_output" == *SQL1611W* ]]; then
            instance_ready=true # The instance is available, with no active databases yet.
        fi
        if [[ "$instance_ready" == true ]]; then
            printf '%s\n' 'Container setup completed and Db2 instance available. Run scripts/init-db.sh, then scripts/verify-db2.sh.'
            exit 0
        fi
    fi
    sleep 5
done
die 'Db2 initialization did not complete. Inspect podman logs db2-rcac; no readiness claim was made.'
