# BenchLab API service graph

BenchLab exposes 11 OpenAPI operations through five generated controllers and five handwritten delegate handlers, plus one internal worker callback endpoint. A handwritten `BenchmarkResource` also registers the same 11 public paths. The generated mappings add JSON media-type conditions, so the mappings are distinct, but route ownership and validation behavior are duplicated. All benchmark operations converge on `BenchmarkServiceImpl`, which fans out to six JPA repositories and `RunEventPublisher`.

Run creation writes a `QUEUED` row to PostgreSQL and publishes the full execution payload to a durable RabbitMQ direct exchange/queue. The Go worker consumes synchronously, invokes the host Docker daemon for compilation and every warm-up/measured iteration, then posts metrics/artifacts to the internal callback. Redis is configured for JHipster user lookup caches; benchmark query results are not cached.

The highest-fanout node is `BenchmarkServiceImpl`. The hottest read paths are recent runs, compare, timeseries, and complexity; they issue per-run metric lookups, and timeseries/complexity first load every run. The main reliability boundary is the database-to-RabbitMQ handoff, which has no outbox/confirm evidence. The worker has no QoS, reconnect loop, bounded retry/DLQ, or health endpoint.

Limitations: generated source is created only during Maven builds; the Docker engine was unavailable, so container measurements and Testcontainers integration tests could not complete. Dual route ownership is statically demonstrated, but runtime handler selection was not exercised because Testcontainers failed first.
