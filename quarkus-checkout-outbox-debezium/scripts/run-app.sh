#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
exec java -Dquarkus.profile=demo -jar target/quarkus-app/quarkus-run.jar
