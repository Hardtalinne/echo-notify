# Architecture

## Text diagram

- Entrypoint: `echo-notify-api` (Ktor REST + OpenAPI)
- Application: use cases for send, process, retry and query status
- Domain: entities, ports and strategies
- Infrastructure: Kafka, PostgreSQL/Flyway, channel adapters, resilience adapters
- Async processing: `echo-notify-consumer` + `echo-notify-worker`
- Observability: W3C trace context propagated in Kafka headers and continued in consumer/worker spans

## Visual suggestion

```mermaid
flowchart TD
  subgraph API
    A1[echo-notify-api]
  end

  subgraph Core
    B1[Domain]
    B2[Application Use Cases]
    B3[Infrastructure Adapters]
  end

  subgraph Async
    C1[echo-notify-consumer]
    C2[echo-notify-worker]
  end

  subgraph Infra
    D1[(PostgreSQL)]
    D6[(Outbox Table)]
    D2[[Kafka Topics]]
    D3[Prometheus]
    D4[Grafana]
    D5[Jaeger]
  end

  A1 --> B2
  B2 --> B1
  B2 --> B3
  B3 --> D1
  B3 --> D6
  D6 --> C2
  B3 --> D2
  C1 --> B2
  C2 --> B2
  A1 --> D3
  C1 --> D3
  C2 --> D3
  D3 --> D4
  A1 --> D5
  C1 --> D5
  C2 --> D5
```
