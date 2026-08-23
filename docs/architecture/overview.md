# Benchmark Platform Overview

## Components

- `benchmark-api` (Spring Boot + JHipster):
  - authentication/authorization
  - implementation and run APIs
  - enqueue run jobs to RabbitMQ
  - persist runs, metrics, and artifacts in PostgreSQL
- `runner-worker` (Go):
  - consumes run jobs from RabbitMQ queue
  - reports a run-start callback, then compiles and executes source in short-lived Docker containers
  - applies network, filesystem, timeout, CPU, and memory controls to runner containers
  - retains bounded stdout/stderr previews and posts terminal results back to the API
- `benchlab-web` (React + Vite):
  - submits quick or broad benchmark presets
  - displays queued/running/completed progress and result charts
  - polls only while work is active, pauses while the document is hidden, and prevents overlapping/stale refreshes
- PostgreSQL stores catalog, run, metric, and artifact state. Redis supports the existing application cache/session integration.

## Boundaries

- API never executes user code directly.
- Worker owns execution lifecycle.
- Communication is asynchronous through RabbitMQ.
- Only generated OpenAPI controllers/delegates own public benchmark routes. A focused internal controller owns worker callbacks.
- Admission limits are enforced in the API before work is published.

## Data Model (MVP)

- `algorithm`
- `dataset`
- `implementation`
- `benchmark_run`
- `run_metric`
- `run_artifact`

## Metric Semantics

- `orchestrationWallTimeMs`: average wall-clock duration of a measured Docker command, including container and process startup.
- `compileWallTimeMs`: wall-clock duration of the compile Docker command, where applicable.
- `cpuTimeMs` and `peakMemoryMb`: unavailable (`null`) because the worker does not currently collect trustworthy measurements.
- Output is a preview, not the complete stream. Each stream retains at most 8,000 bytes during execution and exposes a truncation flag.

These timings support comparative demo exploration on one host, but they are not a substitute for calibrated CPU microbenchmarks.

## Queue Reliability Boundary

The current queue/callback flow is intentionally at-least-once and has known recovery gaps. The next milestone is specified in [Queue Reliability Next Milestone](queue-reliability-next.md).
