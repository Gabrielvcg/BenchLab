# BenchLab Documentation

BenchLab is a Spring Boot benchmark orchestration API, a Go runner worker, and a React dashboard for comparing algorithm implementations across languages.

## Start Here

- [Architecture overview](architecture/overview.md): components, data flow, storage, security boundaries, and deployment shape.
- [Benchmark request lifecycle](architecture/request-lifecycle.md): how a dashboard/API run becomes a RabbitMQ job, Docker execution, and persisted result.
- [Benchmark methodology](benchmarking/methodology.md): what the current measurements mean, known limitations, and what to improve before making scientific claims.
- [Future change guide](development/future-change-guide.md): maintenance rules for future Codex or developer sessions.
- [Local run guide](operations/local-run.md): local services, API, worker, and dashboard startup.
- [VPS runbook](operations/vps-runbook.md): production deployment and recovery notes.

## Generated References

- [Static API service graph summary](architecture/service-graph/architecture-summary.md)
- [Static API service graph Mermaid](architecture/service-graph/service-graph.mmd)
- [Static API service graph JSON](architecture/service-graph/service-graph.json)

The generated service graph is a static-analysis aid, not a replacement for source review. It may include test-only endpoints or low-confidence inferred dependencies.
