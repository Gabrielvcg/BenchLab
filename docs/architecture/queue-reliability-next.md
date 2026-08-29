# Queue Reliability Next Milestone

## Why this is next

The current milestone bounds resource use, validates admission, marks runs as running, and exposes worker readiness. It does not make the database-to-RabbitMQ-to-callback path atomic. A process or network failure can still leave a persisted run without a published message, redeliver work, or cause a completed benchmark to execute again when only its callback failed.

## Coherent follow-up scope

1. Persist run creation and an outbox record in one PostgreSQL transaction.
2. Publish outbox records with RabbitMQ publisher confirms and stable message IDs; mark them published only after confirmation.
3. Configure explicit consumer QoS/prefetch so worker concurrency and queue backpressure are intentional.
4. Make job execution idempotent by run ID and separate **execution complete** from **callback delivered** state.
5. Persist the result locally or durably enough to retry the callback without re-running user code.
6. Add bounded exponential retry with jitter for transient publication/callback failures, followed by a dead-letter queue for exhausted attempts.
7. Add reconciliation for stale `QUEUED`/`RUNNING` records and operational counters for outbox lag, redeliveries, retry exhaustion, DLQ depth, and callback age.
8. Add failure-injection integration tests for API crash after commit, lost publisher acknowledgement, worker restart during execution, duplicate delivery, API outage during callback, and poison messages.

## Product decisions required

- Maximum acceptable queue delay and stale-run timeout.
- Whether the demo should favor strict per-user fairness or global FIFO throughput.
- Retention period for durable result payloads and dead-letter messages.
- Whether multiple worker replicas may execute concurrently and the intended maximum parallel runner-container count per host.

Until this milestone is complete, operators should treat the flow as at-least-once, inspect stale runs and RabbitMQ state, and avoid assuming that a callback failure cannot trigger re-execution.
