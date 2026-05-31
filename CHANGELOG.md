# Changelog

## 0.0.6

- Fixed dashboard refresh selection so the chart stays on the current or latest benchmark algorithm instead of falling back to the first sorted algorithm.
- Tightened sidebar button and selector layout to prevent clipped labels in the production dashboard.
- Centered single-point chart data so sparse benchmark series remain visible.
- Increased demo benchmark input sizes so algorithm work is more visible than container startup overhead.
- Fixed login panel brand and sign-in button alignment.

## 0.0.5

- Required a production-only admin password secret and rotated the seeded JHipster admin password at startup.
- Removed default login credentials from the web dashboard.
- Fixed dashboard button sizing and tightened the production testing layout.
- Fixed Java and C demo execution by compiling runner artifacts into the controlled container temp area.

## 0.0.4

- Added `benchlab-web` dashboard for production testing and benchmark visualization.
- Added benchmark complexity endpoint grouped by language and dataset size.
- Passed dataset size and seed to runner containers through environment variables for realistic input-size benchmarks.

## 0.0.3

- Added GitHub Actions CI/CD for test/Sonar, GHCR image publishing, and VPS deployment.
- Added runtime-only VPS deployment artifacts under `/deploy` for API, worker, PostgreSQL, Redis, and RabbitMQ.
- Added Docker image build support for `runner-worker`.
- Documented required GitHub environment variables and deployment secrets for production.

## 0.0.2

- Added core benchmark domain model (`algorithm`, `dataset`, `implementation`, `benchmark_run`, `run_metric`, `run_artifact`) with Liquibase migration.
- Added benchmark API endpoints for implementation upload, run creation/status, comparison, and timeseries.
- Added RabbitMQ integration for asynchronous run dispatch from API.
- Added `runner-worker` Go service to consume jobs and report run results.
- Implemented real Docker-based worker execution for Python, Java, and C with CPU/memory/timeout constraints and no-network policy.
- Added shared internal callback token (`X-Worker-Token`) between API and worker for result ingestion.
- Added architecture and operations docs under `/docs`.
