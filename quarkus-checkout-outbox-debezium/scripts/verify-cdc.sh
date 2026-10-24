#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
if [[ ! -f target/quarkus-app/quarkus-run.jar ]]; then
  echo 'Build first with ./mvnw package' >&2
  exit 1
fi
exec java -Djava.util.logging.manager=org.jboss.logmanager.LogManager -cp 'target/classes:target/quarkus-app/lib/main/*:target/quarkus-app/lib/boot/*' scripts/CdcVerification.java
