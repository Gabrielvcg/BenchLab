# Changelog

## 0.0.20

- Serialize nullable OpenAPI metrics as numbers/null instead of wrapper objects so saved CPU measurements display correctly.
- Show up to 100 recent persisted runs without an extra hidden-row cutoff; separate successful and failed counters and scope counts to the selected experiment.
- Give compilation an independent bounded 120-second budget, retain execution limits, identify timeout phases and remove named containers after cancellation.
- Verify PostgreSQL-backed metric round trips after flushing the persistence context; preserve historical failed runs and measurements.

## 0.0.19

- Introduce a neutral scientific-workbench theme with white surfaces, slate typography, blue actions, explicit selected states and tabular numeric tables.
- Preserve chart series colours, measurement semantics and the simplified setup/results workflow.

## 0.0.18

- Replace the dense sidebar with a single-column setup/results flow, larger controls and collapsed secondary settings.
- Clear rejected sessions on protected API 401 responses and return to sign-in with an actionable message.

## 0.0.17

- Move the dashboard run action, estimated workload and live feedback above configuration; collapse advanced execution settings.
- Reduce oversized headings and empty charts, add first-run guidance, and improve sidebar scrolling, mobile layout and keyboard focus.
- Preserve existing CPU/wall-time metrics, workload defaults, API contracts and runner behavior; add launch-panel rendering tests.
- Add an optional isolated Chrome UI suite for desktop/mobile layout, metric switching and admission-error feedback.

## 0.0.16

- Fixed Windows Testcontainers integration by using the Docker host and
  published port for Redis-specific tests and keeping the general integration
  suite independent from unnecessary Redisson selectors.
- Removed the generated TLS keystore and static JWT/default credentials before public release.
- Made TLS, broker, worker, monitoring, and control-center credentials external configuration values.
- Added public-release hygiene rules for credential files and documented secret-free local setup.
- Added an optional Redisson Netty thread limit for constrained local test hosts.

## 0.0.15

- Preserved decimal percentile precision in benchmark comparisons, used interpolated percentiles for even sample counts, and displayed timing values consistently in the dashboard.

## 0.0.14

- Ordered the comparison matrix languages from fastest to slowest using the median selected metric across input sizes, with deterministic alphabetical tie-breaking.

## 0.0.13

- Added a collapsible recent-runs panel below the progress counters with status filtering, timestamps, language, input size, and CPU-time details.

## 0.0.12

- Added child-process user plus system CPU-time measurement and made it the default complexity metric to reduce host scheduling noise in cross-language comparisons.
- Added a dashboard selector for CPU time versus in-container wall time, increased the default sample plan to seven measured runs plus two warmups, and exposed CPU time in recent-run summaries.

## 0.0.11

- Added in-container execution wall-time measurement so algorithm complexity views exclude Docker container startup overhead.
- Switched complexity and comparison visualizations to median execution time with standard-deviation error bars and sample context.
- Kept orchestration wall time available as a separate diagnostic metric and marked legacy runs without execution timing for recollection.

## 0.0.10

- Bounded worker stdout/stderr retention during execution and exposed explicit preview truncation metadata.
- Replaced fabricated CPU and memory metrics with nullable unavailable values and renamed Docker wall timings to honest orchestration/compile wall-time fields.
- Added configurable API admission limits, per-user outstanding-work protection, validation responses, and matching OpenAPI/deployment configuration.
- Added a 24-invocation quick-demo preset, bounded-concurrency submission, active-only visibility-aware polling, stale-refresh protection, and run progress feedback.
- Consolidated public benchmark route ownership under generated OpenAPI controllers and isolated authenticated worker callbacks.
- Removed recent-run N+1/full-table benchmark aggregations, added query-backed projections, and added indexes for demonstrated run filters and orderings.
- Expanded CI to Maven verification, Go test/vet, reproducible web install/test/build, and Compose validation.
- Added worker liveness/readiness endpoints and documented the next queue reliability milestone.

## 0.0.9

- Added root documentation covering architecture, request lifecycle, benchmark methodology, local operation, generated service graph references, and future-change guidance.
- Fixed Rust benchmark compilation in `runner-worker` by switching from the broken `rust:1-alpine` image to a full Rust toolchain image that includes `rustc`.
- Fixed Rust compilation under the worker Docker runtime by explicitly adding `/usr/local/cargo/bin` to the compile command path.
- Aligned Rust benchmark compilation to `opt-level=2` so compiled C and Rust runners use comparable optimization levels.
- Raised the dashboard minimum run timeout to 5 seconds and clarified that measured runtime includes isolated Docker/runtime startup overhead.
- Switched measured benchmark aggregation from average wall time to median wall time, with min/mean/max sample context in the runner technical summary.
- Added runner validation that measured iterations must produce deterministic output before timings are accepted.
- Added a worker regression test covering Rust runner preparation so future image changes do not silently break compilation.
- Fixed the production web container healthcheck to probe `127.0.0.1` instead of `localhost`, matching the Nginx listener inside the container.

## 0.0.8

- Added `RUST` as a first-class benchmark language in the dashboard suite and runner worker.
- Added backend language enum support for `RUST` and `ASSEMBLY`, plus worker execution support for assembly source compilation and execution.
- Updated run language selection to show only languages available for the selected benchmark template.

## 0.0.7

- Removed the seeded default `user` account during production startup to prevent `user/user` access in prod environments.
- Kept production admin password rotation enforced through `BENCHLAB_SECURITY_ADMIN_PASSWORD` and refreshed user caches after updates.

## 0.0.6

- Fixed dashboard refresh selection so the chart stays on the current or latest benchmark algorithm instead of falling back to the first sorted algorithm.
- Tightened sidebar button and selector layout to prevent clipped labels in the production dashboard.
- Centered single-point chart data so sparse benchmark series remain visible.
- Increased demo benchmark input sizes so algorithm work is more visible than container startup overhead.
- Fixed login panel brand and sign-in button alignment.
- Separated runner compilation time from measured execution time and added an unmeasured warm-up execution per benchmark job.
- Added Go and Ruby runner support and included both languages in the dashboard demo benchmark.
- Replaced the single demo seed with a usable multi-algorithm benchmark suite (`O(1)`, `O(log n)`, `O(n)`, `O(n log n)`, `O(n^2)`) selectable from the dashboard panel.
- Added configurable benchmark controls in the panel to run one selected algorithm with custom sizes, measured iterations, warm-up iterations, and timeout.

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
