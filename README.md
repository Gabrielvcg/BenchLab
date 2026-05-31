# benchLab

Benchmark platform API for comparing algorithm implementations across programming languages.

## Current Architecture (MVP)

- `benchmark-api` (this repository): JHipster monolith for auth, catalog, run orchestration, and benchmark queries.
- `runner-worker` (`/runner-worker`): Go worker consuming jobs from RabbitMQ and reporting results.
- `benchlab-web` (`/benchlab-web`): lightweight production dashboard for comparing languages by dataset size.
- Persistence: PostgreSQL for transactional data and run artifacts (truncated output + metadata).
- Queue: RabbitMQ for asynchronous run dispatch.

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

4. Ensure worker and API share the same internal token:

```bash
export BENCHLAB_WORKER_TOKEN=benchlab-internal-token
```

Production also requires `BENCHLAB_SECURITY_ADMIN_PASSWORD`; the API uses it to rotate the seeded JHipster `admin` password at startup.

## Main API Endpoints

- `POST /api/implementations`
- `POST /api/runs`
- `GET /api/runs/{id}`
- `GET /api/benchmarks/compare?algorithmId=&datasetId=`
- `GET /api/benchmarks/timeseries?algorithmId=&language=`
- `GET /api/benchmarks/complexity?algorithmId=&metric=wallTimeMs`

## Worker Runtime Notes

- Current execution languages in worker: `PYTHON`, `JAVA`, `C`, `GO`, `RUBY`.
- Execution is isolated with Docker flags: `--network none`, `--read-only`, `--tmpfs /tmp`, memory and CPU limits.
- Base runner image definitions are available under `/runner-images`.
- Each benchmark run supports `iterations` (default `5`) for more stable average timings.
- Worker timings run one warm-up execution before measuring; compiled languages report compilation separately in `compileMs` and keep `wallTimeMs` focused on execution time.

OpenAPI source: `src/main/resources/swagger/api.yml`

## Documentation

- [`/docs/architecture/overview.md`](docs/architecture/overview.md)
- [`/docs/operations/local-run.md`](docs/operations/local-run.md)
- [`/docs/operations/vps-runbook.md`](docs/operations/vps-runbook.md)
