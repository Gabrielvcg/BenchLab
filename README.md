# benchLab

Benchmark platform API for comparing algorithm implementations across programming languages.

## Current Architecture

- `benchmark-api` (this repository): JHipster monolith for auth, catalog, run orchestration, and benchmark queries.
- `runner-worker` (`/runner-worker`): Go worker consuming jobs from RabbitMQ and reporting results.
- `benchlab-web` (`/benchlab-web`): lightweight production dashboard for comparing languages by dataset size, including a seeding suite of simple algorithms across multiple complexity classes.
- Persistence: PostgreSQL for transactional data, measured orchestration timing, and bounded output previews.
- Queue: RabbitMQ for asynchronous run dispatch.
- Cache/session support: Redis through the existing JHipster configuration.

See architecture details at [`/docs/architecture/overview.md`](docs/architecture/overview.md).
VPS runbook: [`/docs/operations/vps-runbook.md`](docs/operations/vps-runbook.md).
Deployment artifacts for GitHub Actions + VPS live under [`/deploy`](deploy).

## Quick Start

1. Start dependencies:

```bash
docker compose -f src/main/docker/services.yml up -d
```

2. Run API:

```bash
./mvnw
```

3. Run worker:

```bash
cd runner-worker
go mod tidy
go run .
```

4. Start the web dashboard:

```bash
cd benchlab-web
npm ci
npm run dev
```

5. Ensure worker and API share the same internal token:

```bash
export BENCHLAB_WORKER_TOKEN=benchlab-internal-token
```

Production also requires `BENCHLAB_SECURITY_ADMIN_PASSWORD`; the API uses it to rotate the seeded JHipster `admin` password at startup.

After signing in, choose **Quick demo (recommended)**. It submits three languages over three sizes with two measured executions and no warm-up: 9 benchmark runs and an estimated 24 Docker invocations. The broader seven-language/four-size preset is deliberately opt-in and estimates 188 invocations. The UI shows submission and queued/running/completed progress and only polls while work is active and the page is visible.

## Main API Endpoints

- `POST /api/implementations`
- `POST /api/runs`
- `GET /api/runs/{id}`
- `GET /api/benchmarks/compare?algorithmId=&datasetId=`
- `GET /api/benchmarks/timeseries?algorithmId=&language=`
- `GET /api/benchmarks/complexity?algorithmId=&metric=orchestrationWallTimeMs`

## Worker Runtime Notes

- Current execution languages in worker: `PYTHON`, `JAVA`, `C`, `GO`, `RUBY`, `RUST`, and `ASSEMBLY`.
- Execution is isolated with Docker flags: `--network none`, `--read-only`, `--tmpfs /tmp`, memory and CPU limits.
- Base runner image definitions are available under `/runner-images`.
- Each benchmark run supports measured `iterations` and unmeasured `warmupIterations` within the configured admission limits.
- `orchestrationWallTimeMs` is the average Docker-command wall time, including container/process startup. Compilation is reported separately as `compileWallTimeMs`.
- CPU time and peak memory are nullable and remain unavailable until a real measurement source is implemented. BenchLab does not substitute wall time or configured memory limits for those measurements.
- Standard output and error are retained as bounded 8,000-byte previews per stream while commands execute. Truncation is explicit in the API response.
- Worker liveness and readiness are exposed on port `8081` at `/health/live` and `/health/ready`.

## Demo Admission Defaults

The server enforces conservative limits before queue publication: 65,536 source bytes, 30-second timeout, 512 MB memory, 2 CPUs, 10 measured iterations, 3 warm-ups, dataset size 25,000,000, and 32 outstanding queued/running jobs per user. The matching `BENCHLAB_LIMITS_*` variables are documented in [`deploy/.env.example`](deploy/.env.example). Environments may lower these values; the OpenAPI contract documents the public demo ceilings.

OpenAPI source: `src/main/resources/swagger/api.yml`

## Documentation

- [`/docs/architecture/overview.md`](docs/architecture/overview.md)
- [`/docs/operations/local-run.md`](docs/operations/local-run.md)
- [`/docs/operations/vps-runbook.md`](docs/operations/vps-runbook.md)
- [`/docs/architecture/queue-reliability-next.md`](docs/architecture/queue-reliability-next.md)
