# Benchmark Platform Overview

## Components

- `benchmark-api` (Spring Boot + JHipster):
  - authentication/authorization
  - implementation and run APIs
  - enqueue run jobs to RabbitMQ
  - persist runs, metrics, and artifacts in PostgreSQL
- `runner-worker` (Go):
  - consumes run jobs from RabbitMQ queue
  - executes benchmark flow (MVP: simulated execution)
  - posts run results back to API callback endpoint

## Boundaries

- API never executes user code directly.
- Worker owns execution lifecycle.
- Communication is asynchronous through RabbitMQ.

## Data Model (MVP)

- `algorithm`
- `dataset`
- `implementation`
- `benchmark_run`
- `run_metric`
- `run_artifact`

## Next Step

Replace simulated worker execution with isolated Docker-per-run execution for Python, Java, C, Go, and Ruby as phase-1 languages.
