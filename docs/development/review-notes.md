# Project Review Notes

## Current Strengths

- The API does not execute submitted code directly; execution is isolated in the worker.
- Worker containers disable networking and run read-only workspaces for measured runs.
- Benchmark compile time is separated from measured execution time.
- Median aggregation reduces sensitivity to occasional Docker startup spikes.
- Deterministic output validation catches unstable snippets before accepting metrics.
- Deployment is runtime-only on the VPS and uses GHCR images.
- Production requires explicit admin-password rotation and worker callback token configuration.

## Main Technical Risks

- Benchmark timings are cold-container wall-clock timings, not pure algorithm CPU timings.
- `cpuTimeMs` is now collected from child-process user plus system CPU time and is the default complexity metric; `executionWallTimeMs` remains available for wall-time analysis.
- The worker mounts the Docker socket, so the worker container is highly privileged in practice.
- Benchmark source templates live in the dashboard, so benchmark definitions are tied to web deployments.
- Generated service graph output includes low-confidence and test-related findings; use it as a map, not as truth.
- `listRecentRuns` is capped to 25 runs, which is fine for the dashboard but not enough for full experiment analysis.

## Good Next Improvements

- Persist raw timing samples for each run.
- Add true CPU-time and measured memory collection.
- Move benchmark templates to backend-managed versioned definitions.
- Add an explicit experiment/batch entity so related runs can be grouped.
- Add a same-container benchmark mode for serious methodology work.
- Add expected-output checks per algorithm/dataset.
- Add role-specific authorization around benchmark mutation endpoints if public users are introduced.
