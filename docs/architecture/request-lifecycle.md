# Benchmark Request Lifecycle

## 1. Dashboard Seeds Catalog Data

The React dashboard keeps a small synthetic suite in `benchlab-web/src/main.tsx`.

When the user clicks `Run selected`, the dashboard:

1. Creates an `algorithm` row through `POST /api/algorithms`.
2. Creates one `dataset` row per selected input size through `POST /api/datasets`.
3. Creates one `implementation` row per selected language through `POST /api/implementations`.
4. Creates one `benchmark_run` row for every implementation/dataset pair through `POST /api/runs`.

The run queue is shuffled before submission to reduce language-order bias.

## 2. API Queues Work

`BenchmarkServiceImpl.createRun` validates the implementation and dataset, creates a `QUEUED` `benchmark_run`, and publishes a `RunRequestedEvent` through `RunEventPublisher`.

The event includes:

- run, implementation, and dataset identifiers
- dataset size and seed
- language and source code
- compile/runtime config fields
- timeout, memory, CPU, iterations, and warmup iterations
- trace ID

Default values are applied when the create-run request omits optional execution controls.

## 3. RabbitMQ Decouples Execution

The queue defaults live in `BenchLabProperties`:

- exchange: `benchlab.run.exchange`
- routing key: `benchlab.run.requested`
- queue: `benchlab.run.requested.q`

The worker consumes `RUN_REQUESTED_QUEUE`, defaulting to `benchlab.run.requested.q`.

## 4. Worker Executes the Job

`runner-worker/main.go` performs the execution lifecycle:

1. Creates a temporary job directory.
2. Writes the submitted source code to a language-specific filename.
3. Selects a Docker image plus compile/run command.
4. Compiles once when the language requires compilation.
5. Runs warmup iterations in isolated containers.
6. Runs measured iterations in isolated containers.
7. Validates that measured stdout/stderr are deterministic.
8. Aggregates measured wall-clock samples using the median.
9. Posts the result to the API callback endpoint.

## 5. API Persists Results

`BenchmarkResource.registerResult` verifies `X-Worker-Token`, then `BenchmarkServiceImpl.registerRunResult` updates:

- `benchmark_run`: status, host, failure reason, timestamps
- `run_metric`: wall time, CPU-time compatibility field, memory, exit code, timeout, compile time
- `run_artifact`: truncated output, checksum, technical summary

## 6. Dashboard Reads Aggregates

The dashboard reads:

- `GET /api/runs` for recent run rows
- `GET /api/benchmarks/complexity?algorithmId=&metric=executionWallTimeMs` for chart series; `orchestrationWallTimeMs` remains available for end-to-end operational timing.
- `GET /api/benchmarks/compare?algorithmId=&datasetId=` for grouped comparison data

The chart explorer displays dataset size on the X axis and milliseconds on the Y axis.
