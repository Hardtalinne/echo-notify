# Local setup

## Requirements
- Docker / Docker Compose

## Run

```bash
docker compose up --build
```

## Health check

```bash
curl http://localhost:8080/health
curl http://localhost:8080/health/ready
```

## Observability
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`
- Jaeger: `http://localhost:16686`
