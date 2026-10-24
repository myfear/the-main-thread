#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
compose up -d postgres kafka connect
wait_for PostgreSQL sql -Atqc 'SELECT 1'
wait_for Kafka compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --list
compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists --topic order-placed --partitions 1 --replication-factor 1
wait_for Connect curl --max-time 3 -fsS "$CONNECT_URL/connectors"
echo 'Infrastructure ready; start Quarkus to apply Flyway before setting up CDC.'
