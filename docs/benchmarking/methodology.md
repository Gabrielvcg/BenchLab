# Benchmark Methodology

## Current Claim Level

BenchLab currently supports useful engineering comparisons and demos, but results should not yet be presented as rigorous scientific language-performance claims.

The platform is more serious than a toy benchmark because it isolates executions, separates compilation time, repeats samples, uses median aggregation, records timing context, and validates deterministic output. The complexity dashboard defaults to child-process CPU time, while in-container wall time remains available for runtime behaviour analysis.

The fairest default comparison is CPU time because it measures user plus system CPU consumed by the benchmark process and its descendants. It is less affected by host scheduling pauses than wall time, but it still includes interpreter/JVM startup and is not a pure algorithm-only counter.

## What `executionWallTimeMs` Means

`executionWallTimeMs` is the median wall-clock time measured by a helper inside the isolated container around the benchmark process. It excludes Docker container creation overhead, but includes interpreter, JVM, or other language-process startup and host scheduling pauses.

When selected, this metric is exposed by the complexity endpoint and dashboard. The chart plots `p50`, shows standard deviation as error bars, and includes average, p95, and sample count on hover.

## What `cpuTimeMs` Means

`cpuTimeMs` is the median user plus system CPU time reported by the isolated helper for the benchmark child process and its descendants. It excludes time while the process is descheduled, but includes runtime startup and system calls. This is the default complexity metric because it reduces scheduling noise when comparing languages.

## What `orchestrationWallTimeMs` Means

`orchestrationWallTimeMs` is the median wall-clock time of measured cold-container executions for one implementation and dataset.

Included in `orchestrationWallTimeMs`:

- Docker container startup
- process/runtime startup
- interpreter or JVM startup when applicable
- program execution
- stdout/stderr capture

Excluded from `orchestrationWallTimeMs`:

- compile time for compiled languages
- API queue time
- RabbitMQ delivery time
- result callback persistence time

Compilation time is stored separately as `compileWallTimeMs`.

`orchestrationWallTimeMs` remains available as an end-to-end operational metric. It is not suitable for inferring algorithmic complexity when the benchmark work is small compared with container startup.

## Noise Controls Already Present

- Runs are asynchronous and isolated from the API process.
- Worker containers run with disabled network and resource limits.
- Compiled languages compile once before measured execution.
- Measured iterations are aggregated by median, not mean.
- Technical summaries include sample count, min, median, mean, and max.
- Measured outputs must be deterministic.
- Dashboard submits run combinations in shuffled order to reduce simple language-order bias.
- Default dashboard measured samples are higher than a single run.
- Default dashboard runs use seven measured samples and two unmeasured samples.
- The default complexity metric is child-process CPU time; in-container wall time can be selected when startup and scheduling behaviour is the subject of the experiment.

## Known Limitations

- Every measured sample starts a fresh Docker container.
- Every execution measurement starts a fresh language process inside that container.
- Warmups also run in fresh containers and do not warm the exact process measured later.
- Java does not benefit from same-process JIT warmup under the current model; warmups are separate isolated processes.
- Python and Ruby timings include interpreter startup every sample.
- Tiny `O(1)` and `O(log n)` tests mostly measure startup overhead.
- The host CPU governor, core pinning, background load, and thermal behavior are not controlled by the app.
- Memory is reported as the configured limit, not measured peak resident memory.
- The benchmark suite is synthetic and small.
- Language implementations live in dashboard source code, so benchmark definitions are versioned with the web app rather than stored as formal datasets.

## Interpreting Results Safely

- Treat CPU results as process CPU comparisons, not pure algorithm-only CPU-time comparisons.
- Prefer larger input sizes where algorithm work dominates startup cost.
- Compare trends across size, not only absolute language rankings.
- Use `technicalLogSummary` to inspect min/median/mean/max before trusting a point.
- Re-run experiments and compare distributions when results are close.
- Avoid claims like "language X is faster than language Y" without describing the timing model.

## Next Steps Toward Paper-Grade Results

1. Add a same-container or same-process harness mode.
2. Add a same-process harness mode so runtime startup can be excluded explicitly.
3. Pin benchmark workers to dedicated CPU cores and document host CPU governor settings.
4. Store raw per-sample measurements, not only summary fields.
5. Record image digests, compiler/runtime versions, host kernel, CPU model, and Docker version with every run batch.
6. Add statistical reports with confidence intervals and outlier policies.
7. Move benchmark suite definitions out of the dashboard into versioned backend-managed benchmark definitions.
8. Add independent correctness checks for expected outputs per dataset.
9. Add language-specific harnesses that preserve fairness without hiding real runtime costs.
