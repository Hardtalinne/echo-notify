# Echo Notify - Technical Overview

## Objective
Production-grade notification microservice with clean architecture, Kafka reliability, retry with exponential backoff, DLQ and observability.

## Functional scope
- Notification channels: Email, Webhook
- Reliability: retry + DLQ + idempotency
- Control: rate limit by type, recipient and client
- Persistence: PostgreSQL + Flyway
- Observability: JSON logs, metrics, tracing

## How to run locally
1. `docker compose up --build`
2. Create a notification:
```bash
curl -X POST http://localhost:8080/v1/notifications \
  -H 'content-type: application/json' \
  -d '{
    "type":"EMAIL",
    "recipient":"user@example.com",
    "clientId":"billing-service",
    "payload":"{\"subject\":\"Invoice\",\"body\":\"Hello\",\"from\":\"no-reply@echo.com\"}",
    "idempotencyKey":"invoice-1001"
  }'
```

## Scaling
- Increase Kafka partitions
- Horizontal scale consumers by same consumer group
- Keep idempotency key unique at DB layer

## Roadmap
- SMS and Push channels
- Multi-tenant
- Feature flags
- Admin dashboard
- Horizontal autoscaling
- Chaos testing
