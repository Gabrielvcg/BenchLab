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

The worker requires a reachable Docker daemon. Its liveness endpoint is `http://localhost:8081/health/live`; readiness becomes successful at `http://localhost:8081/health/ready` after the RabbitMQ consumer is active.

## Web dashboard

```bash
cd benchlab-web
npm ci
npm run dev
```

Use **Quick demo (recommended)** for a small end-to-end path: three languages, three sizes, two measured executions, 9 queued jobs, and an estimated 24 isolated container invocations. The broader comparison is opt-in because it estimates 188 invocations and takes substantially longer.

The progress strip distinguishes queued, running, completed, and failed jobs. Background refresh runs only while queued/running work exists and pauses when the tab is hidden.

## Admission limits

Default demo limits are configurable with environment variables:

| Variable | Default |
| --- | ---: |
| `BENCHLAB_LIMITS_MAX_SOURCE_BYTES` | 65,536 |
| `BENCHLAB_LIMITS_MAX_TIMEOUT_MS` | 30,000 |
| `BENCHLAB_LIMITS_MAX_MEMORY_MB` | 512 |
| `BENCHLAB_LIMITS_MAX_CPU_LIMIT` | 2.0 |
| `BENCHLAB_LIMITS_MAX_ITERATIONS` | 10 |
| `BENCHLAB_LIMITS_MAX_WARMUP_ITERATIONS` | 3 |
| `BENCHLAB_LIMITS_MAX_DATASET_SIZE` | 25,000,000 |
| `BENCHLAB_LIMITS_MAX_OUTSTANDING_RUNS_PER_USER` | 32 |

The API returns validation Problem Details for rejected values and HTTP 429 when the per-user outstanding-work limit is reached.

## RabbitMQ Management UI

- URL: `http://localhost:15672`
- User: `benchlab`
- Password: `benchlab`

## Notes

- Worker callback endpoint is `POST /api/internal/runs/{id}/result` and requires `X-Worker-Token`.
- Worker start callback is `POST /api/internal/runs/{id}/start` and uses the same token.
- Worker executes Docker containers with disabled network and resource limits.
- Reported wall time is orchestration wall time; CPU time and peak memory are unavailable rather than inferred.
