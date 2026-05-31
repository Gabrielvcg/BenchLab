# Local Run Guide

## Services

```bash
docker compose -f src/main/docker/services.yml up -d
```

This starts PostgreSQL, Redis, and RabbitMQ.

## API

```bash
./mvnw
```

## Worker

```bash
cd runner-worker
go mod tidy
go run .
```

Set the same token in both processes:

```bash
export BENCHLAB_WORKER_TOKEN=benchlab-internal-token
```

## RabbitMQ Management UI

- URL: `http://localhost:15672`
- User: `benchlab`
- Password: `benchlab`

## Notes

- Worker callback endpoint is `POST /api/internal/runs/{id}/result` and requires `X-Worker-Token`.
- Worker executes Docker containers with disabled network and resource limits.
