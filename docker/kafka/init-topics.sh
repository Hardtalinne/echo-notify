#!/usr/bin/env bash
set -e

echo "Waiting kafka..."
until /opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --list >/dev/null 2>&1; do
  sleep 2
done

/opt/bitnami/kafka/bin/kafka-topics.sh --create --if-not-exists --bootstrap-server kafka:9092 --topic echo.notify.send --partitions 3 --replication-factor 1
/opt/bitnami/kafka/bin/kafka-topics.sh --create --if-not-exists --bootstrap-server kafka:9092 --topic echo.notify.retry --partitions 3 --replication-factor 1
/opt/bitnami/kafka/bin/kafka-topics.sh --create --if-not-exists --bootstrap-server kafka:9092 --topic echo.notify.dlq --partitions 3 --replication-factor 1

echo "Topics initialized"
