# Runner Worker (Go)

Consumes benchmark jobs from RabbitMQ and reports run results back to `benchmark-api`.

## Timing model

- Compiled languages are compiled once per job before measured execution.
- `compileWallTimeMs` reports Docker-command wall time for compiled languages.
- `orchestrationWallTimeMs` reports median Docker-command wall time after configurable unmeasured warm-up executions. It includes container and process startup.
- `executionWallTimeMs` reports median wall time measured by a helper inside the isolated container around the benchmark process. It excludes Docker container startup but includes language process startup.
- `cpuTimeMs` and `peakMemoryMb` are `null`; the worker does not claim measurements it does not collect.
- Each measured execution still runs in a fresh isolated Docker container.
- Each command retains at most 8,000 bytes of stdout and 8,000 bytes of stderr while it runs. Callback flags identify truncated previews, while `outputSizeBytes` tracks bytes observed across measured iterations.
- The technical log summary includes sample count, minimum, median, mean, and maximum measured wall-clock times.
- Warm-up executions also run in fresh isolated Docker containers, so they validate cold isolated execution but do not warm the exact same process, interpreter, or JVM later measured.
- The timeout budget starts before `docker run`, so it includes container startup plus language runtime startup and the benchmark program itself.
- Measured iterations must produce identical stdout/stderr. Non-deterministic output is reported as a runtime error instead of being aggregated.
- Measured runs report both in-container process wall time and child-process user plus system CPU time. CPU time is the fairer default for cross-language comparisons because it is less affected by host scheduling.

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
