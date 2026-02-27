# Scaling guide

## Kafka scaling
- Increase partition count for `echo.notify.send` and `echo.notify.retry`
- Keep consumer instances in same group to scale horizontally

## Application scaling
- Run multiple `echo-notify-consumer` and `echo-notify-worker` replicas
- Preserve idempotency through DB unique key (`idempotency_key`)

## Database scaling
- Add read replicas for status queries
- Keep writes in primary and optimize indexes by `status`, `client_id`, `idempotency_key`
