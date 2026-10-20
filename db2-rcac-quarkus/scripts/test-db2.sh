#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
load_env
select_database RCACIT
cd "$PROJECT_ROOT"
app_environment ./mvnw verify -Pdb2-integration "$@"
