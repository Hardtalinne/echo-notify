#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
KAFKA_SERVICE="${KAFKA_SERVICE:-kafka}"
TOTAL_REQUESTS="${TOTAL_REQUESTS:-20}"
PAUSE_EVERY="${PAUSE_EVERY:-5}"
PAUSE_SECONDS="${PAUSE_SECONDS:-5}"

send_notification() {
  local index="$1"
  local idempotency_key="chaos-${index}-$(date +%s%N)"

  curl -sS -X POST "${API_BASE_URL}/v1/notifications" \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: ${idempotency_key}" \
    -d '{
      "type": "EMAIL",
      "recipient": "chaos@example.com",
      "clientId": "chaos-suite",
      "payload": {
        "subject": "Chaos test",
        "body": "Kafka intermittent failure test"
      }
    }' >/dev/null
}

for i in $(seq 1 "${TOTAL_REQUESTS}"); do
  send_notification "${i}"
  echo "[chaos] request ${i}/${TOTAL_REQUESTS} enviada"

  if (( i % PAUSE_EVERY == 0 )); then
    echo "[chaos] pausando ${KAFKA_SERVICE} por ${PAUSE_SECONDS}s"
    docker compose pause "${KAFKA_SERVICE}" >/dev/null
    sleep "${PAUSE_SECONDS}"
    echo "[chaos] retomando ${KAFKA_SERVICE}"
    docker compose unpause "${KAFKA_SERVICE}" >/dev/null
  fi

done

echo "[chaos] teste concluído"
echo "[chaos] valide: /health/ready, métricas de retry/dlq e traces no Jaeger"
