import { describe, expect, it } from 'vitest';
import {
  describeApiError,
  estimateDockerInvocations,
  formatMilliseconds,
  hasActiveRuns,
  mapWithConcurrency,
  rankLanguagesByMedianSpeed,
  summarizeRuns,
} from './app-logic';

describe('demo workload logic', () => {
  it('keeps the quick demo intentionally small', () => {
    expect(estimateDockerInvocations(['PYTHON', 'JAVA', 'C'], 3, 2, 0)).toBe(24);
  });

  it('makes the broader comparison cost explicit', () => {
    expect(estimateDockerInvocations(['PYTHON', 'JAVA', 'C', 'GO', 'RUBY', 'RUST', 'ASSEMBLY'], 4, 5, 1)).toBe(188);
  });

  it('shows actionable Problem Details and plain API errors', () => {
    expect(describeApiError(429, 'Too Many Requests', '{"detail":"Outstanding run limit reached"}')).toBe(
      '429 Outstanding run limit reached',
    );
    expect(describeApiError(503, 'Service Unavailable', 'Worker unavailable')).toBe('503 Worker unavailable');
  });

  it('summarizes active and terminal progress', () => {
    const runs = [{ status: 'QUEUED' }, { status: 'RUNNING' }, { status: 'SUCCEEDED' }, { status: 'TIMEOUT' }];
    expect(summarizeRuns(runs)).toEqual({ queued: 1, running: 1, completed: 1, failed: 1, total: 4 });
    expect(hasActiveRuns(runs)).toBe(true);
    expect(hasActiveRuns([{ status: 'SUCCEEDED' }])).toBe(false);
  });

  it('ranks languages from fastest to slowest using median speed', () => {
    expect(
      rankLanguagesByMedianSpeed([
        { language: 'PYTHON', values: [800, 1000, 900] },
        { language: 'JAVA', values: [500, 700, 600] },
        { language: 'RUST', values: [] },
      ]),
    ).toEqual(['JAVA', 'PYTHON', 'RUST']);
    expect(formatMilliseconds(12)).toBe('12 ms');
    expect(formatMilliseconds(12.375)).toBe('12.38 ms');
    expect(
      rankLanguagesByMedianSpeed([
        { language: 'C', values: [12, 20] },
        { language: 'RUST', values: [13, 18] },
      ]),
    ).toEqual(['RUST', 'C']);
  });

  it('bounds concurrent submissions and preserves result order', async () => {
    let active = 0;
    let maxActive = 0;
    const result = await mapWithConcurrency([3, 1, 2, 4], 2, async value => {
      active += 1;
      maxActive = Math.max(maxActive, active);
      await new Promise(resolve => setTimeout(resolve, value));
      active -= 1;
      return value * 2;
    });

    expect(maxActive).toBe(2);
    expect(result).toEqual([6, 2, 4, 8]);
  });
});
