# ADR-002: Kafka reliability model

## Decision
Use separate topics for send/retry/dlq and idempotent producer config (`acks=all`, `enable.idempotence=true`).

## Why
- Better isolation of processing stages
- Easier operations and replay strategy
- Clear observability by queue stage
