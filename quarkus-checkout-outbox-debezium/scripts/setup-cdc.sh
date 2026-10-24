#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
# Application Flyway owns the table. DBA provisioning deliberately runs afterward.
sql < config/debezium/provision.sql
