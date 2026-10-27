# Worker result recovery

## Diagnostic checkpoint (2026-10-23)

Remote `origin/main` at `bb5b09c` still executed `executeRun` before `sendResult` and requeued on callback failure. The original checkout was at `77e8e33` with unrelated uncommitted changes. Implementation is isolated at `C:/Users/GabrielVG/Desktop/PROYECTOS/BenchLab-callback-recovery`, detached at the verified remote revision. No original changes were copied.

## State and guarantees

One worker process exclusively owns one persistent result directory. The existing job ID is the key. A SHA-256 fingerprint of the decoded event binds all known execution fields to that ID; JSON whitespace/property ordering does not change identity. A conflicting event blocks without executing, delivering, or acknowledging it. This is not a distributed execution lock; do not scale replicas or share the directory between processes.

Before new execution the worker validates storage and capacity, then marks the run started. Once execution returns, it retains the result in memory until persistence succeeds. It writes a private temporary file, synchronizes it, renames it over the record, and synchronizes the directory on Linux. Only then does it send the callback. After HTTP success it persists a delivery timestamp, then acknowledges RabbitMQ. Failure in either persistence stage retries that stage without re-execution or premature acknowledgement. A failed delivered-marker write does not resend the callback within that process.

At startup it validates all records and replays pending callbacks before consuming new work. RabbitMQ redelivery finds either a pending result (deliver the same payload) or a delivered result (acknowledge without execution or callback). An acknowledgement failure leaves the durable record available for the next redelivery. Temporary files from incomplete writes are removed at startup; corrupt/oversized records fail closed for operator investigation, rather than silently re-executing.

The API's existing `BenchmarkServiceImpl.registerRunResult` updates metrics and artifacts by run ID. Repeating an identical callback is compatible with those stored values, but updates `finishedAt` again. A lost HTTP response or crash before the delivery marker can therefore cause another callback. This change does not provide exactly-once delivery or execution.

## Bounds and operation

- `WORKER_RESULT_PATH` defaults to `/opt/benchlab/worker-results`. The deployment template mounts the declared `worker_results` named volume there, separate from disposable runner workspaces. Preserve that volume across restarts/replacements; removing it removes recovery history. Standalone container users must provide a persistent mount themselves.
- Each record is limited to 128 KiB; at most 10,000 records are admitted (about 1.22 GiB payload ceiling plus one temporary replacement and filesystem overhead). Defaults are fixed, reviewable first-release choices rather than product retention commitments.
- Delivered records expire after seven days, measured from successful callback delivery. Cleanup happens before new execution. Pending records never expire automatically. A redelivery after its record was cleaned can execute again; queue redelivery delay must fit the retention window for duplicate suppression.
- A full store stops new execution; pre-execution errors are requeued after a one-second pause. Pending callbacks, post-execution disk errors, oversized results, and permanent callback errors retain the job and retry indefinitely, with exponential delays of 1 second up to 60 seconds. Readiness becomes false during retries. There is no retry-exhaustion DLQ in this scope. Fix storage/API availability or investigate invalid data; do not delete pending records to free space unless loss is explicitly accepted.
- Processing is serial and RabbitMQ prefetch is one. A stuck callback intentionally blocks subsequent jobs. Long outages can exceed RabbitMQ's consumer acknowledgement timeout; persisted results survive reconnection/restart, but this change does not add automatic broker reconnection. The existing container restart policy remains relevant.
- Do not place the store on a filesystem without the required file-sync/atomic-rename semantics. Power-loss durability is scoped to Linux with a persistent filesystem honoring synchronization. Windows is supported for development tests, without a power-loss guarantee.

## Crash limits

A crash during execution or between execution finishing and successful persistence can repeat user code. Disk loss, volume loss, concurrent workers, expired delivered history, and storage that ignores synchronization are outside the duplicate-suppression guarantee. A crash after callback acceptance but before marking delivery may repeat the callback, not the persisted calculation. Outbox publication, reconciliation, distributed coordination, DLQ, new metrics, public contracts, and API security remain outside this increment.

## Interview explanation

The expensive benchmark and its network delivery have different failure domains. Requeuing both after an HTTP error repeats user code unnecessarily and can change measurements. A durable completed-result journal makes delivery independently retryable, while delayed acknowledgement and retained delivery records cover restart and redelivery. Bounded storage trades throughput for preservation under failure. The result is deliberately at-least-once with explicit crash windows, rather than an unsupported exactly-once claim.

Startup validation, recovery and admission decode one record at a time; filenames are enumerated in memory, but result bodies are never accumulated across records. Admission still scans retained records, so its I/O cost grows with the bounded store. A regression test traverses 128 records containing 100 KiB payloads and checks live heap growth stays below 8 MiB during traversal.

## Verification evidence (2026-10-23)

- Final `go test ./...`: PASS, `ok github.com/vacaro/benchlab/runner-worker 16.480s` on Windows.
- `go vet ./...`: PASS.
- `GOOS=linux GOARCH=amd64 go build`: PASS (cross-compilation only).
- `git diff --check`: PASS (Git reports only the configured LF/CRLF conversion notice).
- Tests cover HTTP 503 followed by 204 with one calculation; pending-result reopen and replay; lost callback response; delivered redelivery; conflicting event; persistence failure before callback; delivery-marker persistence failure; capacity blocking before start; size rejection; pending retention and delivered expiry; corrupt-store rejection; bounded retry delay; and traversal live-memory regression.
- Persistence errors use deterministic filesystem obstruction, not a physically exhausted disk. Restart coverage reopens the store and runs recovery functions, not a killed production container.
- Docker engine is unavailable (`dockerDesktopLinuxEngine` named pipe missing). Linux runtime fsync/power-loss behavior, live RabbitMQ acknowledgements/redelivery, actual API duplicate callbacks, container restart and Compose execution were not integration-tested. Existing API callback compatibility was inspected in source; no Java changes were made.

## Review inventory

Base: `bb5b09c8b50bcad3a7eea43ab4ec01a851c64579` (`origin/main` fetched at diagnosis). Detached isolated worktree; all changes remain local, unstaged and uncommitted. No push, merge, deployment or frontend edit. Original checkout changes were preserved.

Changed tracked files: root `CHANGELOG.md`, `deploy/docker-compose.yml`, `runner-worker/main.go`, `runner-worker/README.md`, `docs/architecture/queue-reliability-next.md`.

New files: `runner-worker/result_store.go`, `runner-worker/result_store_test.go`, `runner-worker/result_sync_unix.go`, `runner-worker/result_sync_windows.go`, and this document. The adjacent external review patch includes both tracked modifications and new files. Review/acceptance remains pending.

## Deployment gate

Do not deploy this increment until a disposable environment demonstrates a real worker-container restart with its retained volume, RabbitMQ redelivery, and callback retry against the real API. These integration checks remain pending and are required before deployment; passing local unit tests and Linux cross-compilation does not satisfy this gate. No services were started and no workstation Docker configuration was changed for this delivery.

## Second validation round

See [disposable integration fixture and exact evidence](worker-recovery-integration.md). A real OS-process kill/restart regression now passes against the journal; calculation and HTTP API remain fixtures in that test. Full Go tests (10.978s), go vet, six offline harness tests, static structure checks and generated Compose configuration validation pass. Docker remains absent, so real worker-container recreation, RabbitMQ redelivery and real API callbacks have NOT been exercised. The deployment gate remains closed. Product code was not expanded in this round.

Additional files: `runner-worker/result_process_test.go`, `scripts/test-worker-recovery.py`, `scripts/test_worker_recovery_fixture.py`, `scripts/fixtures/worker-recovery/{Dockerfile.api,Dockerfile.api.dockerignore,proxy.py,seed.sql}`, and `docs/architecture/worker-recovery-integration.md`. All remain local and uncommitted. Independent technical acceptance of the production implementation was reported by the orchestrator; harness integration and deployment acceptance remain pending.

## Terminal API callback update

The callback timestamp-rewrite behavior described above is historical evidence from the parent implementation. The current candidate preserves the first committed terminal result, including finishedAt, metrics and artifacts; later valid terminal callbacks are acknowledged without mutation. See [terminal callback handling](terminal-run-callbacks.md) for transaction/locking scope and validation gaps. Real API/broker integration remains pending.
