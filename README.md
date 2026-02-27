# Echo Notify

Echo Notify is a resilient notification microservice built with Kotlin, focused on reliability, observability, and event-driven architecture.

## Services

- `echo-notify-api`
- `echo-notify-consumer`
- `echo-notify-worker`

## Topics

- `echo.notify.send`
- `echo.notify.retry`
- `echo.notify.dlq`

## Quick start

```bash
docker compose up --build
```

Open:
- API: `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`
- Jaeger: `http://localhost:16686`
- MailHog: `http://localhost:8025`

More docs at `docs-public/`.
