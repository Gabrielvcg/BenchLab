# Runner Worker (Go)

Consumes benchmark jobs from RabbitMQ and reports run results back to `benchmark-api`.

## Run

```bash
go mod tidy
go run .
```

## Env vars

- `RABBITMQ_URL` (default `amqp://benchlab:benchlab@localhost:5672/`)
- `RUN_REQUESTED_QUEUE` (default `benchlab.run.requested.q`)
- `BENCHLAB_API_BASE_URL` (default `http://localhost:8080`)
- `BENCHLAB_WORKER_TOKEN` (optional bearer token)
