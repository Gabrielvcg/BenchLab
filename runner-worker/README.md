# Runner Worker (Go)

Consumes benchmark jobs from RabbitMQ and reports run results back to `benchmark-api`.

## Timing model

- Compiled languages are compiled once per job before measured execution.
- `compileMs` reports compile time for compiled languages.
- `wallTimeMs` and `cpuTimeMs` report the average measured execution time after one unmeasured warm-up execution.
- Each measured execution still runs in a fresh isolated Docker container.

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
