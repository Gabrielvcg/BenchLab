# Runner Worker (Go)

Consumes benchmark jobs from RabbitMQ and reports run results back to `benchmark-api`.

## Timing model

- Compiled languages are compiled once per job before measured execution.
- `compileWallTimeMs` reports Docker-command wall time for compiled languages.
- `orchestrationWallTimeMs` reports median Docker-command wall time after configurable unmeasured warm-up executions. It includes container and process startup.
- `executionWallTimeMs` reports median wall time measured by a helper inside the isolated container around the benchmark process. It excludes Docker container startup but includes language process startup.
- `cpuTimeMs` reports child-process CPU time when available; `peakMemoryMb` stays `null` because it is not measured.
- Each measured execution still runs in a fresh isolated Docker container.
- Each command retains at most 8,000 bytes of stdout and 8,000 bytes of stderr while it runs. Callback flags identify truncated previews, while `outputSizeBytes` tracks bytes observed across measured iterations.
- The technical log summary includes sample count, minimum, median, mean, and maximum measured wall-clock times.
- Warm-up executions also run in fresh isolated Docker containers, so they validate cold isolated execution but do not warm the exact same process, interpreter, or JVM later measured.
- The timeout budget starts before `docker run`, so it includes container startup plus language runtime startup and the benchmark program itself.
- Measured iterations must produce identical complete stdout/stderr stream digests. Output beyond the 8,000-byte previews participates in this check. Non-deterministic output is reported as a runtime error instead of being aggregated.
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

## Completed result recovery

Set `WORKER_RESULT_PATH` to a private persistent directory (default `/opt/benchlab/worker-results`). Run exactly one worker owning that directory. The deployment template declares and mounts the `worker_results` named volume. Completed results are saved before callback delivery and replayed after restart; callback failures block new work with bounded exponential backoff. Delivered records are retained for seven days; pending records are retained until delivered. The store admits at most 10,000 records of 128 KiB each and stops new execution when full. See [guarantees, recovery, crash windows and verification](../docs/architecture/worker-result-recovery.md).

## Output identity and helper metadata

Complete-stream digests and user-byte counts are independent of bounded previews. The final token-bound helper frame is parsed in a fixed 256-byte tail buffer, excluded from user identity/counts, and cannot be lost merely because user stderr filled the preview. Artifact checksums use domain-separated full stdout/stderr digests; historical prefix checksums have different semantics. See [output capture protocol, limits and tests](../docs/benchmarking/worker-output-capture.md).
