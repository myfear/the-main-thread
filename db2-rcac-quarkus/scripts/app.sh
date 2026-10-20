#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
load_env
select_database RCACLAB
cd "$PROJECT_ROOT"
case "${1:-dev}" in
    dev) app_environment ./mvnw quarkus:dev ;;
    jar) app_environment java -jar target/quarkus-app/quarkus-run.jar ;;
    *) die 'Usage: app.sh [dev|jar]' ;;
esac
