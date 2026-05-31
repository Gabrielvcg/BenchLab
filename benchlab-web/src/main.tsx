import React from 'react';
import { createRoot } from 'react-dom/client';
import { Activity, BarChart3, CirclePlay, LogOut, RefreshCw, ShieldCheck } from 'lucide-react';
import './styles.css';

type Algorithm = {
  id: number;
  name: string;
  category: string;
  version: string;
  complexityDeclared: string;
};

type ComplexityPoint = {
  datasetId: number;
  datasetSize: number;
  avg: number;
  stddev: number;
  p50: number;
  p95: number;
  validSamples: number;
};

type ComplexitySeries = {
  language: string;
  points: ComplexityPoint[];
};

type ComplexityResponse = {
  algorithmId: number;
  metric: string;
  series: ComplexitySeries[];
};

type RunSummary = {
  id: number;
  status: string;
  language: string;
  algorithmId: number;
  algorithmName: string;
  datasetId: number;
  datasetSize: number;
  queuedAt: string | null;
  finishedAt: string | null;
  wallTimeMs: number | null;
};

type AuthResponse = {
  id_token: string;
};

const languageColors: Record<string, string> = {
  C: '#0e7c66',
  JAVA: '#c44536',
  PYTHON: '#3166b1',
  GO: '#008f9c',
  RUBY: '#9b1d48',
  CPP: '#6c5ce7',
};

function App() {
  const [token, setToken] = React.useState(() => localStorage.getItem('benchlab.token') ?? '');
  const [login, setLogin] = React.useState('');
  const [password, setPassword] = React.useState('');
  const [algorithms, setAlgorithms] = React.useState<Algorithm[]>([]);
  const [selectedAlgorithmId, setSelectedAlgorithmId] = React.useState<number | null>(null);
  const [complexity, setComplexity] = React.useState<ComplexityResponse | null>(null);
  const [runs, setRuns] = React.useState<RunSummary[]>([]);
  const [message, setMessage] = React.useState('Ready');
  const [busy, setBusy] = React.useState(false);

  const authHeaders = React.useMemo(() => ({ Authorization: `Bearer ${token}` }), [token]);

  async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
    const response = await fetch(path, {
      ...options,
      headers: {
        ...(token ? authHeaders : {}),
        ...(options.body ? { 'Content-Type': 'application/json' } : {}),
        ...(options.headers ?? {}),
      },
    });
    if (!response.ok) {
      throw new Error(`${response.status} ${response.statusText}`);
    }
    if (response.status === 204) {
      return undefined as T;
    }
    return response.json() as Promise<T>;
  }

  async function signIn(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setMessage('Authenticating');
    try {
      const auth = await api<AuthResponse>('/api/authenticate', {
        method: 'POST',
        body: JSON.stringify({ username: login, password }),
      });
      localStorage.setItem('benchlab.token', auth.id_token);
      setToken(auth.id_token);
      setMessage('Authenticated');
    } catch (error) {
      setMessage(`Login failed: ${(error as Error).message}`);
    } finally {
      setBusy(false);
    }
  }

  async function refresh(nextAlgorithmId = selectedAlgorithmId) {
    if (!token) return;
    setBusy(true);
    try {
      const [algorithmData, runData] = await Promise.all([api<Algorithm[]>('/api/algorithms'), api<RunSummary[]>('/api/runs')]);
      setAlgorithms(algorithmData);
      setRuns(runData);
      const algorithmId = nextAlgorithmId ?? algorithmData[0]?.id ?? null;
      setSelectedAlgorithmId(algorithmId);
      if (algorithmId) {
        setComplexity(await api<ComplexityResponse>(`/api/benchmarks/complexity?algorithmId=${algorithmId}&metric=wallTimeMs`));
      } else {
        setComplexity(null);
      }
      setMessage('Data refreshed');
    } catch (error) {
      setMessage(`Refresh failed: ${(error as Error).message}`);
    } finally {
      setBusy(false);
    }
  }

  async function runDemo() {
    setBusy(true);
    setMessage('Seeding benchmark demo');
    try {
      const suffix = new Date().toISOString().replace(/[-:.TZ]/g, '').slice(0, 12);
      const algorithm = await api<Algorithm>('/api/algorithms', {
        method: 'POST',
        body: JSON.stringify({
          name: `sum-loop-${suffix}`,
          category: 'synthetic',
          version: 'v1',
          complexityDeclared: 'O(n)',
        }),
      });

      const sizes = [10000, 50000, 100000, 250000];
      const datasets = [];
      for (const size of sizes) {
        datasets.push(
          await api<{ id: number }>('/api/datasets', {
            method: 'POST',
            body: JSON.stringify({
              type: 'synthetic-loop',
              sizeValue: size,
              seed: 42,
              checksum: `synthetic-${size}`,
              datasetVersion: `n-${size}`,
            }),
          }),
        );
      }

      const implementations = await Promise.all([
        api<{ id: number }>('/api/implementations', {
          method: 'POST',
          body: JSON.stringify({
            algorithmId: algorithm.id,
            language: 'PYTHON',
            sourceCode:
              "import os\nn=int(os.getenv('BENCHLAB_DATASET_SIZE','1000'))\ns=0\nfor i in range(n):\n    s += (i % 97)\nprint(s)",
            compileConfig: '',
            runtimeConfig: '',
          }),
        }),
        api<{ id: number }>('/api/implementations', {
          method: 'POST',
          body: JSON.stringify({
            algorithmId: algorithm.id,
            language: 'JAVA',
            sourceCode:
              'public class Main { public static void main(String[] args) { long n = Long.parseLong(System.getenv().getOrDefault("BENCHLAB_DATASET_SIZE", "1000")); long s = 0; for (long i = 0; i < n; i++) { s += i % 97; } System.out.println(s); } }',
            compileConfig: '',
            runtimeConfig: '',
          }),
        }),
        api<{ id: number }>('/api/implementations', {
          method: 'POST',
          body: JSON.stringify({
            algorithmId: algorithm.id,
            language: 'C',
            sourceCode:
              '#include <stdio.h>\n#include <stdlib.h>\nint main(){ long n = atol(getenv("BENCHLAB_DATASET_SIZE")); long s = 0; for(long i=0;i<n;i++){ s += i % 97; } printf("%ld\\n", s); return 0; }',
            compileConfig: '',
            runtimeConfig: '',
          }),
        }),
      ]);

      for (const implementation of implementations) {
        for (const dataset of datasets) {
          await api('/api/runs', {
            method: 'POST',
            body: JSON.stringify({
              implementationId: implementation.id,
              datasetId: dataset.id,
              timeoutMs: 15000,
              memoryMb: 256,
              cpuLimit: 1,
              iterations: 5,
            }),
          });
        }
      }

      setMessage('Demo queued. Refreshing while worker processes runs');
      await refresh(algorithm.id);
    } catch (error) {
      setMessage(`Demo failed: ${(error as Error).message}`);
    } finally {
      setBusy(false);
    }
  }

  React.useEffect(() => {
    if (token) {
      refresh();
      const interval = window.setInterval(() => refresh(), 6000);
      return () => window.clearInterval(interval);
    }
    return undefined;
  }, [token]);

  if (!token) {
    return (
      <main className="login-shell">
        <form className="login-panel" onSubmit={signIn}>
          <div className="brand-row">
            <ShieldCheck size={28} />
            <span>BenchLab</span>
          </div>
          <label>
            User
            <input value={login} onChange={(event) => setLogin(event.target.value)} autoComplete="username" placeholder="User" />
          </label>
          <label>
            Password
            <input
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              type="password"
              autoComplete="current-password"
              placeholder="Password"
            />
          </label>
          <button type="submit" disabled={busy}>
            <CirclePlay size={18} />
            <span>Sign in</span>
          </button>
          <p>{message}</p>
        </form>
      </main>
    );
  }

  return (
    <main className="dashboard">
      <aside className="sidebar">
        <div className="brand-row">
          <Activity size={28} />
          <span>BenchLab</span>
        </div>
        <select
          value={selectedAlgorithmId ?? ''}
          onChange={(event) => refresh(Number(event.target.value))}
          aria-label="Algorithm"
        >
          {algorithms.map((algorithm) => (
            <option key={algorithm.id} value={algorithm.id}>
              {algorithm.name}
            </option>
          ))}
        </select>
        <button onClick={runDemo} disabled={busy}>
          <CirclePlay size={18} />
          <span>Run demo</span>
        </button>
        <button onClick={() => refresh()} disabled={busy}>
          <RefreshCw size={18} />
          <span>Refresh</span>
        </button>
        <button
          onClick={() => {
            localStorage.removeItem('benchlab.token');
            setToken('');
          }}
        >
          <LogOut size={18} />
          <span>Sign out</span>
        </button>
        <p className="status-text">{message}</p>
      </aside>

      <section className="workspace">
        <header>
          <div>
            <p className="eyebrow">Production laboratory</p>
            <h1>Algorithm runtime by input size</h1>
          </div>
          <div className="metric-pill">
            <BarChart3 size={18} />
            wallTimeMs
          </div>
        </header>
        <ComplexityChart complexity={complexity} />
        <RunTable runs={runs} />
      </section>
    </main>
  );
}

function ComplexityChart({ complexity }: { complexity: ComplexityResponse | null }) {
  const allPoints = complexity?.series.flatMap((series) => series.points) ?? [];
  if (!complexity || allPoints.length === 0) {
    return <section className="empty-chart">No benchmark points yet. Run the demo or launch runs from the API.</section>;
  }

  const width = 980;
  const height = 430;
  const padding = { top: 28, right: 34, bottom: 58, left: 76 };
  const minX = Math.min(...allPoints.map((point) => point.datasetSize));
  const maxX = Math.max(...allPoints.map((point) => point.datasetSize));
  const maxY = Math.max(...allPoints.map((point) => point.avg), 1);
  const xScale = (value: number) => padding.left + ((value - minX) / Math.max(maxX - minX, 1)) * (width - padding.left - padding.right);
  const yScale = (value: number) => height - padding.bottom - (value / maxY) * (height - padding.top - padding.bottom);

  const ticks = Array.from(new Set(allPoints.map((point) => point.datasetSize))).sort((a, b) => a - b);

  return (
    <section className="chart-surface">
      <svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Complexity chart">
        <line x1={padding.left} y1={padding.top} x2={padding.left} y2={height - padding.bottom} className="axis" />
        <line x1={padding.left} y1={height - padding.bottom} x2={width - padding.right} y2={height - padding.bottom} className="axis" />
        {[0, 0.25, 0.5, 0.75, 1].map((step) => {
          const y = yScale(maxY * step);
          return (
            <g key={step}>
              <line x1={padding.left} y1={y} x2={width - padding.right} y2={y} className="grid" />
              <text x={padding.left - 12} y={y + 4} textAnchor="end" className="tick">
                {Math.round(maxY * step)}
              </text>
            </g>
          );
        })}
        {ticks.map((tick) => (
          <text key={tick} x={xScale(tick)} y={height - 22} textAnchor="middle" className="tick">
            {tick.toLocaleString()}
          </text>
        ))}
        {complexity.series.map((series) => {
          const line = series.points.map((point) => `${xScale(point.datasetSize)},${yScale(point.avg)}`).join(' ');
          const color = languageColors[series.language] ?? '#475569';
          return (
            <g key={series.language}>
              <polyline points={line} fill="none" stroke={color} strokeWidth="4" strokeLinejoin="round" strokeLinecap="round" />
              {series.points.map((point) => (
                <circle key={`${series.language}-${point.datasetId}`} cx={xScale(point.datasetSize)} cy={yScale(point.avg)} r="5" fill={color} />
              ))}
            </g>
          );
        })}
      </svg>
      <div className="legend">
        {complexity.series.map((series) => (
          <span key={series.language}>
            <i style={{ background: languageColors[series.language] ?? '#475569' }} />
            {series.language}
          </span>
        ))}
      </div>
    </section>
  );
}

function RunTable({ runs }: { runs: RunSummary[] }) {
  return (
    <section className="table-surface">
      <h2>Recent runs</h2>
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Status</th>
              <th>Algorithm</th>
              <th>Language</th>
              <th>Size</th>
              <th>Wall ms</th>
            </tr>
          </thead>
          <tbody>
            {runs.map((run) => (
              <tr key={run.id}>
                <td>{run.id}</td>
                <td>
                  <span className={`run-status ${run.status.toLowerCase()}`}>{run.status}</span>
                </td>
                <td>{run.algorithmName}</td>
                <td>{run.language}</td>
                <td>{run.datasetSize?.toLocaleString()}</td>
                <td>{run.wallTimeMs ?? '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

createRoot(document.getElementById('root')!).render(<App />);
