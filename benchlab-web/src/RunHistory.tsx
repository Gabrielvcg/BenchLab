import React from 'react';
import { formatMilliseconds } from './app-logic';

export type HistoryRun = {
  id: number; algorithmId: number; algorithmName: string; status: string; language: string;
  datasetSize: number; queuedAt: string | null; cpuTimeMs: number | null; executionWallTimeMs: number | null;
};

export function groupHistory(runs: HistoryRun[]) {
  const groups = new Map<number, {id:number; name:string; runs:HistoryRun[]}>();
  for (const run of runs) {
    const group = groups.get(run.algorithmId) ?? {id:run.algorithmId, name:run.algorithmName, runs:[]};
    group.runs.push(run);
    groups.set(run.algorithmId, group);
  }
  return [...groups.values()];
}

export function RunHistory({ loadPage, onCompare }: {
  loadPage: (beforeId: number | undefined, signal: AbortSignal) => Promise<HistoryRun[]>;
  onCompare: (algorithmId:number) => void;
}) {
  const [runs, setRuns] = React.useState<HistoryRun[]>([]);
  const [loaded, setLoaded] = React.useState(false);
  const [more, setMore] = React.useState(false);
  const [busy, setBusy] = React.useState(false);
  const [error, setError] = React.useState('');
  const pending = React.useRef<AbortController | null>(null);
  React.useEffect(() => () => pending.current?.abort(), []);
  async function load(older = false) {
    if (pending.current) return;
    const controller = new AbortController();
    pending.current = controller;
    setBusy(true); setError('');
    try {
      const cursor = older ? runs[runs.length - 1]?.id : undefined;
      const page = await loadPage(cursor, controller.signal);
      if (controller.signal.aborted) return;
      setRuns(current => older ? [...current, ...page.filter(row => !current.some(existing => existing.id === row.id))] : page);
      setLoaded(true); setMore(page.length === 100);
    } catch (cause) {
      if (!controller.signal.aborted) setError(`Could not load history: ${(cause as Error).message}`);
    } finally {
      pending.current = null;
      if (!controller.signal.aborted) setBusy(false);
    }
  }
  return <section className="chart-surface history-panel" aria-label="My run history">
    <div className="section-heading"><div><h2>My run history</h2><p>Previous experiments stay saved. Open one to inspect its runs or compare its results.</p></div>
      <button className="sort-chip" onClick={() => load()} disabled={busy}>{busy ? 'Loading…' : loaded ? 'Refresh history' : 'Load my history'}</button>
    </div>
    {error && <p role="alert">{error}</p>}
    {loaded && !runs.length && <p>No runs saved for this account yet.</p>}
    {groupHistory(runs).map(group => <details key={group.id} className="history-experiment">
      <summary>{group.name} <span>#{group.id} · {group.runs.length} loaded runs</span></summary>
      <button className="sort-chip" onClick={() => onCompare(group.id)}>Compare this experiment</button>
      <div className="table-scroll"><table><thead><tr><th>Run</th><th>Queued</th><th>Language</th><th>Input size</th><th>Status</th><th>CPU</th><th>Wall time</th></tr></thead>
        <tbody>{group.runs.map(run => <tr key={run.id}><td>#{run.id}</td><td>{run.queuedAt ? new Date(run.queuedAt).toLocaleString() : '—'}</td><td>{run.language}</td><td>{run.datasetSize.toLocaleString()}</td><td>{run.status}</td><td>{run.cpuTimeMs == null ? '—' : formatMilliseconds(run.cpuTimeMs)}</td><td>{run.executionWallTimeMs == null ? '—' : formatMilliseconds(run.executionWallTimeMs)}</td></tr>)}</tbody></table></div>
    </details>)}
    {more && <button className="sort-chip" disabled={busy} onClick={() => load(true)}>Load older runs</button>}
    {loaded && <p className="field-help">{runs.length} runs loaded for this account. {more ? 'Load older runs to extend the history; an experiment may span pages.' : 'End of history.'}</p>}
  </section>;
}
