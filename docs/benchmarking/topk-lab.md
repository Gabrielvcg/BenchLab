# Same-JVM top-k algorithm lab

## Problem and diagnostic checkpoint

Select the k highest-scoring backend records, for example a ranked alert shortlist. Score descending, then ID ascending defines the comparator; equal score/ID records retain multiplicity. k is clamped to n, zero returns empty and negative k is rejected. Input is not mutated. Output is ordered by the defined comparator, but ordering alone is not considered proof of correct selection.

At base `bb5b09c8b50bcad3a7eea43ab4ec01a851c64579`, no equivalent top-k experiment was found in backend/scripts/docs. The existing smoke registers sorting metadata but executes a print; the platform methodology notes separate-process warmups. This standalone teaching lab addresses same-JVM measurement without changing that platform. Isolated checkout: `C:/Users/GabrielVG/Desktop/PROYECTOS/BenchLab-topk-lab`. The callback-recovery checkout and delivery packages were not changed.

## Reproduce with Java 17

No Maven dependencies, Spring application, Docker or services are involved. The executable standalone Java source is outside application sources. From the repository root, use an empty temporary output directory for class files:

```text
javac --release 17 -d PATH_TO_TEMP_CLASSES benchmarks/topk/TopKLab.java
java -cp PATH_TO_TEMP_CLASSES TopKLab --test
java -Xms128m -Xmx256m -cp PATH_TO_TEMP_CLASSES TopKLab NEW_RESULTS_DIRECTORY
```

The result directory must not exist. Class files are build outputs and should stay outside the repository. An external process timeout is recommended when reproducing; the harness checks a 25-second campaign budget between sample pairs, not a hard wall-clock cancellation of a running operation. Fixed n={1000,10000,50000}, k={10,n/2,n}, 12 warmup pairs and 12 measured pairs bound this small default campaign. No performance assertion is a functional test.

## Correctness and measurement boundary

The deterministic tests compare both strategies and a TreeMap multiset reference against an independent quadratic rank-counting oracle for small inputs. Cases include empty, singleton, k=0, k>n, exact duplicates, equal scores with distinct IDs, negative scores, extreme integer/long values, negative k and nonmutation. Twenty seeds each exercise every k from 0 through 33 for n=31. A hand-specified tie fixture also checks expected membership.

Before timing each campaign case, both strategies are compared against a TreeMap reference; every result is checked again outside the timed region. Both receive the same immutable input objects generated once per n with java.util.Random(20261023+n), unique sequential IDs and repeated scores in 0..999. A small known-input oracle catches shared-comparator mistakes; the larger reference shares the comparator deliberately. This is not an exhaustive proof for all Java objects or arbitrary comparators.

Timing uses System.nanoTime around only the strategy call. It includes algorithm-owned copying, allocations, heap operations and ordered-output construction; excludes dataset generation, reference validation, checksum consumption, CSV output, compilation and JVM startup. It is elapsed wall time, including pauses/descheduling, not CPU time. Every returned item contributes to a checksum written to a volatile sink and raw output, making consumption observable outside timing. Each pair runs both strategies; first strategy alternates by pair and case, equally balanced in warmups and measurement. Warmups and measurements run in the SAME JVM. Raw rows record n/k/actual seed/repetition/position/strategy/nanoseconds/checksum.

## Cost from the implementation

Let m=min(k,n). For m=0 both return immediately. Full sort copies n references, sorts n records and copies the first m: worst-case O(n log n + m) time and O(n) auxiliary storage including sort working space. Java's adaptive object sort may exploit existing order; this dataset is random with repeated scores.

The bounded heap keeps its worst selected item at the root. Each scanned item either fills the heap, is rejected after one comparison, or replaces the root. Selection is O(n log(m+1)) worst case, followed by O(m log m) output sorting; O(m) additional storage. More precisely rejected items cost O(1) each; actual replacement count depends on input order/distribution. For m=1 selection is linear. For k near n, heap work and final sorting eliminate the small-k advantage; at k=n both have O(n log n) time, and heap bookkeeping can cost more. This implementation materializes input for fairness; a real streaming heap design could avoid materializing all n records. Allocation/memory complexity is reasoned from code, not measured heap bytes.

## Actual local observations (2026-10-23)

Campaign completed in 1,494,349,800 ns including setup/warmups/validation/output (about 1.49s); 216 measured rows. Runtime: Temurin 17.0.8.1, Windows 11 amd64, 28 available processors, Intel64 Family 6 Model 183 Stepping 1, -Xms128m -Xmx256m. Compiler javac 17.0.16 with --release 17. Full runtime/configuration is in [metadata](topk-results/local-2026-10-23/metadata.properties); all real samples are in [CSV](topk-results/local-2026-10-23/samples.csv).

| n | k | Full sort median (us) | Bounded heap median (us) |
| ---: | ---: | ---: | ---: |
| 1000 | 10 | 176.75 | 16.35 |
| 1000 | 500 | 158.60 | 132.65 |
| 1000 | 1000 | 172.45 | 179.45 |
| 10000 | 10 | 1730.90 | 91.80 |
| 10000 | 5000 | 1523.50 | 1298.00 |
| 10000 | 10000 | 1504.25 | 1709.10 |
| 50000 | 10 | 9079.05 | 93.60 |
| 50000 | 25000 | 8878.50 | 7870.20 |
| 50000 | 50000 | 8908.50 | 10074.75 |

Small k favored the heap in these samples, while k=n favored full sorting at every tested n. The half-n case was closer. These are descriptive medians of one local campaign, not estimates of universal speedups. In particular the almost-flat small-k heap median between 10,000 and 50,000 highlights that JIT phase and small sample durations complicate curve reading; it does not establish constant-time scanning. Complexity follows from loops/data structures, not fitted curves.

## Limits and evidence

This is a didactic harness, not JMH: no independent JVM forks, JIT stabilization detection, compiler/inlining diagnostics, CPU pinning, controlled frequency/load/thermals, GC/allocation attribution, confidence intervals or timer-overhead calibration. One operation per sample may be very short. Reused inputs may be cache-hot; fixed ascending case order couples n/k with JVM age despite balanced strategy order. Warmups cannot prove steady state. Other score distributions, input orders, seeds and realistic record shapes may change the crossover. Do not extrapolate to production latency or claim heap is always faster.

Verified locally: javac --release 17; deterministic --test; one complete bounded campaign; CSV shape (216 rows, 12 per strategy/case, both order positions, consistent checksums); git diff --check. Maven verify was not run: application sources/build/dependencies are unchanged, and the documented standalone Java checks exercise this lab directly. No API/DB/security/frontend/CI/deployment edits, no persistent process, commit or push. Independent technical review accepted this snapshot as an educational artifact on 2026-10-23; this is not production or publication acceptance.

Interview explanation: I separated selection cost from process startup, held the input and ordering contract fixed, verified membership against an independent oracle, and balanced execution order within one JVM. A bounded heap saves sorting all n items when k is small, but it still scans n and must sort its k outputs. When k approaches n, full sorting can be simpler and faster. The local measurements illustrate that tradeoff; they do not prove the asymptotic bounds or a universal winner.


## Independent technical review

Report retained outside the repository at `C:/Users/GabrielVG/Documents/Codex/benchlab-topk-review-2026-10-23`. The reviewer reported 7,366 independent correctness checks passing, reproduced the Java 17 campaign in about 1.47 seconds, validated both 216-row CSVs and recomputed the published median table. No source repair was requested. These are reviewer-reported checks; the author did not rerun them during this documentation update. The accepted scope remains didactic: no JMH equivalence, production latency claim, universal speedup or complexity inferred solely from measurements. Source, raw local samples and application/worker behavior are unchanged.
