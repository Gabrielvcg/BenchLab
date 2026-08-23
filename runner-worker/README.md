# Runner Worker (Go)

Consumes benchmark jobs from RabbitMQ and reports run results back to `benchmark-api`.

## Timing model

- Compiled languages are compiled once per job before measured execution.
- `compileWallTimeMs` reports Docker-command wall time for compiled languages.
- `orchestrationWallTimeMs` reports average Docker-command wall time after configurable unmeasured warm-up executions. It includes container and process startup.
- `cpuTimeMs` and `peakMemoryMb` are `null`; the worker does not claim measurements it does not collect.
- Each measured execution still runs in a fresh isolated Docker container.
- Each command retains at most 8,000 bytes of stdout and 8,000 bytes of stderr while it runs. Callback flags identify truncated previews, while `outputSizeBytes` tracks bytes observed across measured iterations.

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
- `WORKER_HEALTH_ADDRESS` (default `:8081`)

## Health

- `GET /health/live` confirms the process and health server are running.
- `GET /health/ready` returns success only after the RabbitMQ consumer is active.

Operational logs are in Spanish and identify job/trace IDs without source, tokens, or output bodies.

The next reliability milestone is documented in [`/docs/architecture/queue-reliability-next.md`](../docs/architecture/queue-reliability-next.md).
