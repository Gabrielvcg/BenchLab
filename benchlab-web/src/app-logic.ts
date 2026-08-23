export type RunState = { status: string };

export type RunProgress = {
  queued: number;
  running: number;
  completed: number;
  failed: number;
  total: number;
};

const COMPILED_LANGUAGES = new Set(['JAVA', 'C', 'GO', 'RUST', 'ASSEMBLY']);

export function describeApiError(status: number, statusText: string, rawBody: string): string {
  let detail = rawBody.trim();
  try {
    const problem = JSON.parse(rawBody) as { detail?: string; message?: string; title?: string };
    detail = problem.detail ?? problem.message ?? problem.title ?? detail;
  } catch {
    // Keep the plain response body when it is not JSON.
  }
  return `${status} ${detail || statusText}`;
}

export function summarizeRuns(runs: RunState[]): RunProgress {
  return runs.reduce<RunProgress>(
    (summary, run) => {
      summary.total += 1;
      if (run.status === 'QUEUED') summary.queued += 1;
      else if (run.status === 'RUNNING') summary.running += 1;
      else {
        summary.completed += 1;
        if (run.status !== 'SUCCEEDED') summary.failed += 1;
      }
      return summary;
    },
    { queued: 0, running: 0, completed: 0, failed: 0, total: 0 },
  );
}

export function hasActiveRuns(runs: RunState[]): boolean {
  return runs.some(run => run.status === 'QUEUED' || run.status === 'RUNNING');
}

export function estimateDockerInvocations(
  languages: string[],
  datasetCount: number,
  measuredIterations: number,
  warmupIterations: number,
): number {
  return languages.reduce((total, language) => {
    const compileInvocation = COMPILED_LANGUAGES.has(language) ? 1 : 0;
    return total + datasetCount * (compileInvocation + measuredIterations + warmupIterations);
  }, 0);
}

export async function mapWithConcurrency<T, R>(
  items: T[],
  concurrency: number,
  task: (item: T, index: number) => Promise<R>,
  onCompleted?: (completed: number, total: number) => void,
): Promise<R[]> {
  if (items.length === 0) return [];
  const results = new Array<R>(items.length);
  let nextIndex = 0;
  let completed = 0;

  async function worker() {
    while (nextIndex < items.length) {
      const index = nextIndex;
      nextIndex += 1;
      results[index] = await task(items[index], index);
      completed += 1;
      onCompleted?.(completed, items.length);
    }
  }

  const workerCount = Math.min(Math.max(1, concurrency), items.length);
  await Promise.all(Array.from({ length: workerCount }, () => worker()));
  return results;
}
