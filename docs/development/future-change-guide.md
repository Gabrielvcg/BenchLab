# Future Change Guide

This guide is for future Codex sessions and maintainers. It explains where changes belong and what must be updated together.

## Ground Rules

- Keep code, comments, DTOs, API contracts, docs, and commit messages in English.
- Keep application log messages in Spanish.
- Do not commit secrets or real deployment `.env` files.
- Preserve unrelated local changes.
- Update `CHANGELOG.md` for user-visible, operational, benchmark-methodology, API, deployment, or developer-workflow changes.
- Prefer small, evidence-backed changes over broad refactors.

## Where To Change Things

- Backend HTTP endpoints: `src/main/java/com/vacaro/benchlab/web/rest/benchmark/BenchmarkResource.java`
- Benchmark business logic: `src/main/java/com/vacaro/benchlab/service/impl/BenchmarkServiceImpl.java`
- Benchmark DTOs: `src/main/java/com/vacaro/benchlab/service/dto/benchmark`
- Persistence model: `src/main/java/com/vacaro/benchlab/domain` plus Liquibase changelog under `src/main/resources/config/liquibase/changelog`
- Queue settings: `src/main/java/com/vacaro/benchlab/config/BenchLabProperties.java` and RabbitMQ config
- Run event contract: `src/main/java/com/vacaro/benchlab/service/messaging/RunRequestedEvent.java` and `runner-worker/main.go`
- Worker execution model: `runner-worker/main.go`
- Worker tests: `runner-worker/main_test.go`
- Dashboard templates/charts: `benchlab-web/src/main.tsx` and `benchlab-web/src/styles.css`
- Production deployment: `.github/workflows/ci-cd.yml`, `deploy/docker-compose.yml`, `deploy/.env.example`, and `deploy/nginx/benchlab.conf`
- Operations docs: `docs/operations`
- Architecture/methodology docs: `docs/architecture` and `docs/benchmarking`

## Change Coupling Checklist

When adding or changing a runner language:

- Update `ImplementationLanguage` enum in the backend.
- Update worker `prepareRunnerSpec`.
- Add worker tests for image, compile command, and run command.
- Update dashboard language types, colors, available languages, and benchmark template sources.
- Update benchmark methodology docs with compile/runtime details.
- Verify all template outputs match for representative sizes.

When changing run event fields:

- Update backend `RunRequestedEvent`.
- Update worker `RunRequestedEvent`.
- Update create-run DTO and OpenAPI schema if the field is user-facing.
- Update dashboard request payloads if relevant.
- Add or update integration tests.

When changing metrics:

- Decide whether the database schema needs a Liquibase migration.
- Update `RunMetric`, DTOs, service mapping, OpenAPI, dashboard types, and charts.
- Update `docs/benchmarking/methodology.md`.
- Avoid overloading `cpuTimeMs` unless it is truly CPU time.

When changing deployment:

- Keep `/deploy/.env.example` aligned with `.github/workflows/ci-cd.yml`.
- Keep `docs/operations/vps-runbook.md` aligned with Compose service names, ports, and required secrets.
- Verify `docker compose --env-file .env -f deploy/docker-compose.yml config` on the VPS shape.

## Verification Checklist

Run the smallest meaningful checks first:

```bash
cd runner-worker
go test ./...
```

For backend changes:

```bash
./mvnw -ntp --batch-mode test
```

For dashboard changes:

```bash
npm --prefix benchlab-web install
npm --prefix benchlab-web run build
```

If dependencies are not installed locally, state that clearly in the final handoff instead of pretending the build passed.

## Documentation Checklist

Update docs when any of these change:

- public API behavior
- queue contracts
- worker execution model
- benchmark timing semantics
- supported languages
- deployment variables, secrets, ports, or service names
- local run commands
- security assumptions

Generated service-graph files live under `docs/architecture/service-graph`. Regenerate them after substantial backend endpoint or dependency changes:

```bash
python C:\Users\GabrielVG\.codex\skills\api-service-graph\scripts\api_service_graph.py --root . --out docs\architecture\service-graph
```

Review generated output before relying on it; static analysis can detect test-only endpoints and low-confidence inferred dependencies.
