# Event Flow

1. API receives `POST /v1/notifications`
2. Idempotency and rate limit checks
3. Notification persisted as `PENDING` and outbox event is stored in the same DB transaction
4. `echo-notify-worker` outbox loop publishes pending events to `echo.notify.send`
5. `echo-notify-consumer` processes and sends via strategy channel with trace context extraction from Kafka headers
6. On transient failure, status set to `FAILED` and event goes to `echo.notify.retry`
7. `echo-notify-worker` retries with exponential backoff and trace context continuation
8. On max attempts reached, message is published to `echo.notify.dlq`

```mermaid
flowchart LR
  A[Client] --> B[echo-notify-api]
  B --> C[(PostgreSQL)]
  B --> O[(notification_outbox)]
  O --> H[echo-notify-worker outbox publisher]
  H --> D[[echo.notify.send]]
  D --> E[echo-notify-consumer]
  E --> F{Send success?}
  F -->|yes| C
  F -->|no| G[[echo.notify.retry]]
  G --> W[echo-notify-worker retry]
  W --> I{Max retries reached?}
  I -->|no| G
  I -->|yes| J[[echo.notify.dlq]]
  J --> C
```
