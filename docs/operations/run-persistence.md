# Run persistence and timeout diagnostics

Runs are stored in PostgreSQL before queue dispatch. Worker results update `benchmark_run`, `run_metric` and `run_artifact`. Production Compose mounts the existing named `postgresql_data` volume. Ordinary pull/up deployments do not remove that volume. Never use `down -v` to upgrade.

A read-only production check on 2026-09-05 found all 28 records #483–#510, including five TIMEOUT records and CPU values for successful runs. Both #488 and #497 failed at compilation, near the former 30-second budget. The UI's latest-25 window hid three failures, and its additional 24-row cutoff hid another record. These are visibility issues, not evidence of missing database records. Nullable OpenAPI wrappers also prevented saved numeric values from displaying in the recent-run list.

The list now returns at most 100 rows and displays that window explicitly. It is not unlimited history or pagination. Older records remain accessible by ID. Counts describe the selected experiment within that window and distinguish success from failure. Failed historical runs are preserved; this release does not retry them or fabricate missing measurements.

Compilation defaults to 120000 ms, independently from requested execution timeout. `WORKER_COMPILE_TIMEOUT_MS` accepts 1000–300000 ms; invalid values fall back to 120000. This changes the build budget, not the measured process interval. Deploy Compose exposes the setting; the existing deployment uses its default unless explicitly configured. Named container cleanup uses an independent 10-second deadline after cancellation. Host contention can still cause failure; a longer build budget does not guarantee success.

PostgreSQL integration tests flush/clear the persistence context before reading saved status and assert numeric CPU/wall metrics through both list/detail API paths. Queue outbox, publisher confirms and callback retry without re-execution remain separate reliability work; this change does not promise exactly-once execution.
