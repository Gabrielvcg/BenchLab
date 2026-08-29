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

The API listens on `http://localhost:8080` by default.

On Windows hosts, integration tests use the Docker host together with the
published PostgreSQL port. The general application integration suite uses an
in-memory cache because those tests do not exercise Redis; this avoids opening
Redisson selectors in every Spring context. Redis-specific test infrastructure
remains available for focused tests, and uses the Docker host plus published
port rather than the container's internal address. Production keeps its normal
Redisson cache configuration.

## Worker

```bash
cd runner-worker
go mod tidy
go run .
```

Set the same token in both processes:

```bash
export BENCHLAB_WORKER_TOKEN="$(openssl rand -hex 32)"
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

| Variable                                        |    Default |
| ----------------------------------------------- | ---------: |
| `BENCHLAB_LIMITS_MAX_SOURCE_BYTES`              |     65,536 |
| `BENCHLAB_LIMITS_MAX_TIMEOUT_MS`                |     30,000 |
| `BENCHLAB_LIMITS_MAX_MEMORY_MB`                 |        512 |
| `BENCHLAB_LIMITS_MAX_CPU_LIMIT`                 |        2.0 |
| `BENCHLAB_LIMITS_MAX_ITERATIONS`                |         10 |
| `BENCHLAB_LIMITS_MAX_WARMUP_ITERATIONS`         |          3 |
| `BENCHLAB_LIMITS_MAX_DATASET_SIZE`              | 25,000,000 |
| `BENCHLAB_LIMITS_MAX_OUTSTANDING_RUNS_PER_USER` |         32 |

The API returns validation Problem Details for rejected values and HTTP 429 when the per-user outstanding-work limit is reached.

## RabbitMQ Management UI

- URL: `http://localhost:15672`
- User: `benchlab` (configured by `RABBITMQ_USER`)
- Password: provide `RABBITMQ_PASSWORD` through your local environment; no default password is committed.

## Notes

- Worker callback endpoint is `POST /api/internal/runs/{id}/result` and requires `X-Worker-Token`.
- Worker start callback is `POST /api/internal/runs/{id}/start` and uses the same token.
- Worker executes Docker containers with disabled network and resource limits.
- Dashboard benchmark templates live in `benchlab-web/src/main.tsx`.
- Current chart timings use median in-container process wall-clock milliseconds.
- Reported orchestration wall time remains the median measured Docker-command duration; CPU time and peak memory are unavailable rather than inferred.
