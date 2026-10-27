# Disposable worker recovery integration fixture

## Status and prerequisites

The full scenario is prepared but NOT executed. Docker Engine is unavailable on the current workstation (`dockerDesktopLinuxEngine` pipe absent); no daemon, host service, installation or configuration was started or changed. This fixture does not satisfy the deployment gate until it passes on a disposable runner.

Use a disposable machine dedicated exclusively to this test, with an already working local Linux Docker Engine, Docker Compose v2, Python 3, and outbound access for image/Maven downloads. The nested Docker service requires `privileged: true`. This is not a security isolation boundary and must never be enabled on production or a shared workstation to run this test. No production workflow is changed. The fixture does not mount any host Docker socket; the real worker talks only to a dedicated nested daemon over the fixture network. Do not expose that daemon beyond this disposable environment.

Run from the isolated repository root, without Python optimization (`-O` disables assertions and is rejected):

```text
python -B scripts/test-worker-recovery.py --check
python -B scripts/test_worker_recovery_fixture.py -v
python -B scripts/test-worker-recovery.py --run-on-disposable-host
```

The final command explicitly acknowledges the disposable-host prerequisite. It first inspects the current Docker context and engine read-only, rejects inherited DOCKER_HOST/DOCKER_CONTEXT and TCP contexts, and requires Linux containers. It does not start or reconfigure Docker. Local socket inspection cannot prove the host is disposable: the operator must ensure that prerequisite.

## Scenario

1. Generate `bl-recovery-<32 lowercase UUID hex>` and a temporary JSON Compose manifest. Build API from current Java sources with the repository Maven wrapper and worker from its existing Dockerfile. No frontend build, dependency changes or production Compose reuse. The API build copies only required backend inputs; Windows Maven trust-store configuration is deliberately not copied into the Linux image.
2. Start fresh PostgreSQL, Redis, RabbitMQ, real API, fault proxy, and privileged nested Docker. Only proxy and RabbitMQ management bind to host loopback on dynamically allocated ports. Credentials and rows are fictitious. Seed SQL is applied exclusively through this generated project's database container after real API readiness/migrations. Seed rows bypass public admission deliberately: this test targets execution/callback recovery, not authentication or admission.
3. Pull the Python runner in the nested engine, start the real worker with a 256 MiB limit and dedicated result/workspace volumes, and publish one persistent event to the real exchange.
4. Let the benchmark run once, force result callbacks to return 503, verify the pending persisted result and count exactly one real benchmark container via nested Docker events.
5. SIGKILL the worker container, wait for the unacknowledged RabbitMQ delivery to return, inspect its `redelivered` flag with requeue, remove/recreate the worker container without deleting volumes, and observe a callback replay while the proxy still fails. Verify the journal contents remain identical and no new benchmark container appears.
6. Recover the proxy, require a durable delivered marker, queue acknowledgement, real database terminal state and one metric/artifact row. Republish the identical event and require another acknowledgement with neither another callback nor another calculation.
7. Publish a second run while the proxy forwards an accepted API callback but drops its response. Require callback retry against the real API, one metric/artifact row, and exactly two benchmark containers total (one per distinct run). The existing API may update `finishedAt` on duplicate callback; the fixture does not assert timestamp immutability.

On success stdout contains a JSON PASS summary with the generated project, actual benchmark-container count, callback statistics and restart/redelivery flags. Every wait and subprocess has a finite timeout; failures return nonzero. Save stdout/stderr as review evidence. Image tags are fixture versions, not a permanent reproducibility guarantee: record image IDs/digests when executing for deployment evidence.

## Host-enforced resource budget

Before executing, provision a dedicated disposable VM with a hard maximum of 4 vCPUs, 8 GiB RAM and a 40 GiB disposable disk, plus an external 60-minute job/VM lifetime deadline. These are a proposed finite test budget, not measured minimum requirements; if insufficient, record the failure and review a new budget rather than silently removing limits. Enforce CPU/RAM/disk through the VM/runner platform and the wall-clock deadline through its job timeout/automatic teardown. Record the actual enforced limits with the result. Do not create or change these settings on the current workstation for this task.

The harness does NOT impose or verify these global quotas. Its per-operation timeouts are not an overall runtime limit, the worker's 256 MiB limit and API's 512 MiB Java heap do not bound the whole stack, and nested Docker/build cache/image storage can exhaust the host disk. If the external deadline kills the script, its finally cleanup may not run: destroy the dedicated VM after collecting evidence. Unique resource names scope cleanup ownership, not CPU/RAM/disk isolation.

## Cleanup and limits

Cleanup runs even after a partial build/start. It uses only the generated project and exact manifest; the project-name guard rejects arbitrary names. It removes that project's containers, networks and volumes plus its two unique built-image tags. It never runs prune or deletes arbitrary paths. Downloaded base images/build cache may remain; destroying the disposable machine removes those. If cleanup fails, the exact printed manifest is retained for diagnosis. Use that exact manifest/project to retry cleanup; do not substitute production names. Successful host-file cleanup uses unlink and empty-directory removal, never recursive deletion.

This harness is not power-loss testing and does not validate distributed workers, outbox, DLQ or unlimited retention. Linux fsync runtime, real API/RabbitMQ interactions and privileged nested Docker startup are still unverified here. Compose schema validation does not prove successful image build or service startup.

## Evidence from the current workstation (2026-10-23)

- Read-only `docker version`: client present, engine absent; full integration NOT run.
- `go test -run 'TestJournalSurvivesKilledProcess|TestJournalProcessHelper' -count=1 -v ./...`: PASS, 1.532s. A real child OS process executes a fixture calculation, syncs the production journal and receives HTTP 503; the parent forcibly kills it, starts a second process, and checks callback delivery, delivered marker and a single execution marker. Uses a local fake HTTP API and fixture calculation, not RabbitMQ, the worker main loop or Docker.
- `go test -count=1 ./...`: PASS, 10.978s including the subprocess regression.
- `go vet ./...`: PASS.
- `python -B scripts/test_worker_recovery_fixture.py -v`: six tests PASS. Covers fault rejection/forwarding/response loss, manifest isolation assertions and refusal of remote daemon contexts. Uses mocked upstream connections, not the real API.
- `python -B scripts/test-worker-recovery.py --check`: PASS, fixture files/structure and generated-name guards.
- Generated manifest checked with `docker compose --file <temporary manifest> config --quiet`: PASS, no daemon/service invocation.

The production implementation and harness design/offline evidence received independent technical ACCEPT in `C:/Users/GabrielVG/Documents/Codex/benchlab-independent-review-2026-10-23/REPORT.md` and its `harness-round/REPORT.md` addendum. The addendum predates this runbook snapshot; its missing-file observation was temporal. Full integration execution and deployment acceptance remain pending. No deployment acceptance is implied. Require process exit code zero, not merely the printed PASS summary: cleanup runs after the summary and can still fail.

The harness does not deliberately exceed the broker consumer acknowledgement timeout, exercise automatic container restart after broker closure, assert readiness transitions, or test physical ENOSPC. Those behaviors are not validated by this scenario or its offline checks.

## Updated API candidate

The historical finishedAt-rewrite limitation above describes the earlier snapshot. The terminal-callback candidate now treats the first committed result as authoritative; duplicate and contradictory terminal callbacks do not mutate it. The existing fixture still checks payloads/row counts and does not itself prove lock behavior or timestamp preservation against a real database. See [terminal callback handling](terminal-run-callbacks.md).
