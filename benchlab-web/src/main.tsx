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

type ImplementationLanguage = 'PYTHON' | 'JAVA' | 'C' | 'GO' | 'RUBY';

type AlgorithmTemplate = {
  key: string;
  name: string;
  category: string;
  complexityDeclared: string;
  datasetSizes: number[];
  sources: Record<ImplementationLanguage, string>;
};

const BENCHMARK_SUITE: AlgorithmTemplate[] = [
  {
    key: 'constant-read',
    name: 'constant-read',
    category: 'synthetic',
    complexityDeclared: 'O(1)',
    datasetSizes: [1000000, 5000000, 10000000, 25000000],
    sources: {
      PYTHON:
        "import os\nn=int(os.getenv('BENCHLAB_DATASET_SIZE','1000'))\na=n%97\nb=(n*3)%101\nprint((a+b)%997)",
      JAVA:
        'public class Main { public static void main(String[] args) { long n = Long.parseLong(System.getenv().getOrDefault("BENCHLAB_DATASET_SIZE", "1000")); long a = n % 97; long b = (n * 3) % 101; System.out.println((a + b) % 997); } }',
      C: '#include <stdio.h>\n#include <stdlib.h>\nint main(){ long n = atol(getenv("BENCHLAB_DATASET_SIZE")); long a = n % 97; long b = (n * 3) % 101; printf("%ld\\n", (a + b) % 997); return 0; }',
      GO: 'package main\n\nimport (\n\t"fmt"\n\t"os"\n\t"strconv"\n)\n\nfunc main() {\n\tn, _ := strconv.ParseInt(os.Getenv("BENCHLAB_DATASET_SIZE"), 10, 64)\n\ta := n % 97\n\tb := (n * 3) % 101\n\tfmt.Println((a + b) % 997)\n}',
      RUBY: "n = ENV.fetch('BENCHLAB_DATASET_SIZE', '1000').to_i\na = n % 97\nb = (n * 3) % 101\nputs((a + b) % 997)",
    },
  },
  {
    key: 'log-halving',
    name: 'log-halving',
    category: 'synthetic',
    complexityDeclared: 'O(log n)',
    datasetSizes: [1000000, 5000000, 10000000, 25000000],
    sources: {
      PYTHON:
        "import os\nn=int(os.getenv('BENCHLAB_DATASET_SIZE','1000'))\nsteps=0\nwhile n>1:\n    n//=2\n    steps+=1\nprint(steps)",
      JAVA:
        'public class Main { public static void main(String[] args) { long n = Long.parseLong(System.getenv().getOrDefault("BENCHLAB_DATASET_SIZE", "1000")); long steps = 0; while (n > 1) { n /= 2; steps++; } System.out.println(steps); } }',
      C: '#include <stdio.h>\n#include <stdlib.h>\nint main(){ long n = atol(getenv("BENCHLAB_DATASET_SIZE")); long steps = 0; while(n > 1){ n /= 2; steps++; } printf("%ld\\n", steps); return 0; }',
      GO: 'package main\n\nimport (\n\t"fmt"\n\t"os"\n\t"strconv"\n)\n\nfunc main() {\n\tn, _ := strconv.ParseInt(os.Getenv("BENCHLAB_DATASET_SIZE"), 10, 64)\n\tvar steps int64\n\tfor n > 1 {\n\t\tn /= 2\n\t\tsteps++\n\t}\n\tfmt.Println(steps)\n}',
      RUBY: "n = ENV.fetch('BENCHLAB_DATASET_SIZE', '1000').to_i\nsteps = 0\nwhile n > 1\n  n /= 2\n  steps += 1\nend\nputs steps",
    },
  },
  {
    key: 'linear-sum',
    name: 'linear-sum',
    category: 'synthetic',
    complexityDeclared: 'O(n)',
    datasetSizes: [1000000, 5000000, 10000000, 25000000],
    sources: {
      PYTHON:
        "import os\nn=int(os.getenv('BENCHLAB_DATASET_SIZE','1000'))\ns=0\nfor i in range(n):\n    s += (i % 97)\nprint(s)",
      JAVA:
        'public class Main { public static void main(String[] args) { long n = Long.parseLong(System.getenv().getOrDefault("BENCHLAB_DATASET_SIZE", "1000")); long s = 0; for (long i = 0; i < n; i++) { s += i % 97; } System.out.println(s); } }',
      C: '#include <stdio.h>\n#include <stdlib.h>\nint main(){ long n = atol(getenv("BENCHLAB_DATASET_SIZE")); long s = 0; for(long i=0;i<n;i++){ s += i % 97; } printf("%ld\\n", s); return 0; }',
      GO: 'package main\n\nimport (\n\t"fmt"\n\t"os"\n\t"strconv"\n)\n\nfunc main() {\n\tn, _ := strconv.ParseInt(os.Getenv("BENCHLAB_DATASET_SIZE"), 10, 64)\n\tvar s int64\n\tfor i := int64(0); i < n; i++ {\n\t\ts += i % 97\n\t}\n\tfmt.Println(s)\n}',
      RUBY: "n = ENV.fetch('BENCHLAB_DATASET_SIZE', '1000').to_i\ns = 0\n(0...n).each do |i|\n  s += i % 97\nend\nputs s",
    },
  },
  {
    key: 'linearithmic-mix',
    name: 'linearithmic-mix',
    category: 'synthetic',
    complexityDeclared: 'O(n log n)',
    datasetSizes: [100000, 250000, 500000, 1000000],
    sources: {
      PYTHON:
        "import os\nn=int(os.getenv('BENCHLAB_DATASET_SIZE','1000'))\ns=0\nfor i in range(1,n+1):\n    x=i\n    while x>1:\n        x//=2\n        s += x & 1\nprint(s)",
      JAVA:
        'public class Main { public static void main(String[] args) { long n = Long.parseLong(System.getenv().getOrDefault("BENCHLAB_DATASET_SIZE", "1000")); long s = 0; for (long i = 1; i <= n; i++) { long x = i; while (x > 1) { x /= 2; s += x & 1; } } System.out.println(s); } }',
      C: '#include <stdio.h>\n#include <stdlib.h>\nint main(){ long n = atol(getenv("BENCHLAB_DATASET_SIZE")); long s = 0; for(long i=1;i<=n;i++){ long x=i; while(x>1){ x/=2; s += x & 1; } } printf("%ld\\n", s); return 0; }',
      GO: 'package main\n\nimport (\n\t"fmt"\n\t"os"\n\t"strconv"\n)\n\nfunc main() {\n\tn, _ := strconv.ParseInt(os.Getenv("BENCHLAB_DATASET_SIZE"), 10, 64)\n\tvar s int64\n\tfor i := int64(1); i <= n; i++ {\n\t\tx := i\n\t\tfor x > 1 {\n\t\t\tx /= 2\n\t\t\ts += x & 1\n\t\t}\n\t}\n\tfmt.Println(s)\n}',
      RUBY: "n = ENV.fetch('BENCHLAB_DATASET_SIZE', '1000').to_i\ns = 0\n(1..n).each do |i|\n  x = i\n  while x > 1\n    x /= 2\n    s += (x & 1)\n  end\nend\nputs s",
    },
  },
  {
    key: 'quadratic-nested',
    name: 'quadratic-nested',
    category: 'synthetic',
    complexityDeclared: 'O(n^2)',
    datasetSizes: [2000, 4000, 6000, 8000],
    sources: {
      PYTHON:
        "import os\nn=int(os.getenv('BENCHLAB_DATASET_SIZE','1000'))\ns=0\nfor i in range(n):\n    for j in range(n):\n        s += (i + j) & 1\nprint(s)",
      JAVA:
        'public class Main { public static void main(String[] args) { int n = Integer.parseInt(System.getenv().getOrDefault("BENCHLAB_DATASET_SIZE", "1000")); long s = 0; for (int i = 0; i < n; i++) { for (int j = 0; j < n; j++) { s += (i + j) & 1; } } System.out.println(s); } }',
      C: '#include <stdio.h>\n#include <stdlib.h>\nint main(){ int n = atoi(getenv("BENCHLAB_DATASET_SIZE")); long s = 0; for(int i=0;i<n;i++){ for(int j=0;j<n;j++){ s += (i + j) & 1; } } printf("%ld\\n", s); return 0; }',
      GO: 'package main\n\nimport (\n\t"fmt"\n\t"os"\n\t"strconv"\n)\n\nfunc main() {\n\tn, _ := strconv.Atoi(os.Getenv("BENCHLAB_DATASET_SIZE"))\n\tvar s int64\n\tfor i := 0; i < n; i++ {\n\t\tfor j := 0; j < n; j++ {\n\t\t\ts += int64((i + j) & 1)\n\t\t}\n\t}\n\tfmt.Println(s)\n}',
      RUBY: "n = ENV.fetch('BENCHLAB_DATASET_SIZE', '1000').to_i\ns = 0\n(0...n).each do |i|\n  (0...n).each do |j|\n    s += (i + j) & 1\n  end\nend\nputs s",
    },
  },
];

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
  const [selectedTemplateKey, setSelectedTemplateKey] = React.useState(BENCHMARK_SUITE[2]?.key ?? BENCHMARK_SUITE[0].key);
  const [suiteSizesInput, setSuiteSizesInput] = React.useState((BENCHMARK_SUITE[2]?.datasetSizes ?? BENCHMARK_SUITE[0].datasetSizes).join(','));
  const [runIterations, setRunIterations] = React.useState(5);
  const [warmupIterations, setWarmupIterations] = React.useState(1);
  const [runTimeoutMs, setRunTimeoutMs] = React.useState(60000);
  const selectedAlgorithmIdRef = React.useRef<number | null>(selectedAlgorithmId);

  const authHeaders = React.useMemo(() => ({ Authorization: `Bearer ${token}` }), [token]);
  const selectedTemplate = React.useMemo(
    () => BENCHMARK_SUITE.find((item) => item.key === selectedTemplateKey) ?? BENCHMARK_SUITE[0],
    [selectedTemplateKey],
  );

  React.useEffect(() => {
    selectedAlgorithmIdRef.current = selectedAlgorithmId;
  }, [selectedAlgorithmId]);

  React.useEffect(() => {
    setSuiteSizesInput(selectedTemplate.datasetSizes.join(','));
  }, [selectedTemplate]);

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

  async function refresh(nextAlgorithmId?: number | null) {
    if (!token) return;
    setBusy(true);
    try {
      const [algorithmData, runData] = await Promise.all([api<Algorithm[]>('/api/algorithms'), api<RunSummary[]>('/api/runs')]);
      setAlgorithms(algorithmData);
      setRuns(runData);
      const currentAlgorithmId = selectedAlgorithmIdRef.current;
      const currentStillExists = currentAlgorithmId != null && algorithmData.some((algorithm) => algorithm.id === currentAlgorithmId);
      const latestRunAlgorithmId = runData.find((run) => algorithmData.some((algorithm) => algorithm.id === run.algorithmId))?.algorithmId ?? null;
      const algorithmId = nextAlgorithmId ?? (currentStillExists ? currentAlgorithmId : latestRunAlgorithmId) ?? algorithmData[0]?.id ?? null;
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

  async function runSelectedTemplate() {
    setBusy(true);
    setMessage(`Seeding algorithm ${selectedTemplate.name}`);
    try {
      const parsedSizes = suiteSizesInput
        .split(',')
        .map((item) => Number(item.trim()))
        .filter((value) => Number.isFinite(value) && value > 0);
      const uniqueSizes = Array.from(new Set(parsedSizes));
      if (uniqueSizes.length === 0) {
        throw new Error('Sizes must contain at least one positive integer');
      }

      const suffix = new Date().toISOString().replace(/[-:.TZ]/g, '').slice(0, 12);
      const algorithm = await api<Algorithm>('/api/algorithms', {
        method: 'POST',
        body: JSON.stringify({
          name: `${selectedTemplate.name}-${suffix}`,
          category: selectedTemplate.category,
          version: 'v1',
          complexityDeclared: selectedTemplate.complexityDeclared,
        }),
      });

      const datasets: Array<{ id: number }> = [];
      for (const size of uniqueSizes) {
        datasets.push(
          await api<{ id: number }>('/api/datasets', {
            method: 'POST',
            body: JSON.stringify({
              type: selectedTemplate.key,
              sizeValue: size,
              seed: 42,
              checksum: `${selectedTemplate.key}-${size}`,
              datasetVersion: `n-${size}`,
            }),
          }),
        );
      }

      const implementations = await Promise.all(
        (Object.entries(selectedTemplate.sources) as Array<[ImplementationLanguage, string]>).map(([language, sourceCode]) =>
          api<{ id: number }>('/api/implementations', {
            method: 'POST',
            body: JSON.stringify({
              algorithmId: algorithm.id,
              language,
              sourceCode,
              compileConfig: '',
              runtimeConfig: '',
            }),
          }),
        ),
      );

      for (const implementation of implementations) {
        for (const dataset of datasets) {
          await api('/api/runs', {
            method: 'POST',
            body: JSON.stringify({
              implementationId: implementation.id,
              datasetId: dataset.id,
              timeoutMs: runTimeoutMs,
              memoryMb: 256,
              cpuLimit: 1,
              iterations: runIterations,
              warmupIterations,
            }),
          });
        }
      }

      setMessage('Runs queued. Refreshing while worker processes runs');
      await refresh(algorithm.id);
    } catch (error) {
      setMessage(`Run failed: ${(error as Error).message}`);
    } finally {
      setBusy(false);
    }
  }

  React.useEffect(() => {
    if (token) {
      refresh();
      const interval = window.setInterval(() => refresh(selectedAlgorithmIdRef.current), 6000);
      return () => window.clearInterval(interval);
    }
    return undefined;
  }, [token]);

  if (!token) {
    return (
      <main className="login-shell">
        <form className="login-panel" onSubmit={signIn}>
          <div className="brand-row login-brand">
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
        <select value={selectedTemplateKey} onChange={(event) => setSelectedTemplateKey(event.target.value)} aria-label="Algorithm template">
          {BENCHMARK_SUITE.map((template) => (
            <option key={template.key} value={template.key}>
              {template.name} ({template.complexityDeclared})
            </option>
          ))}
        </select>
        <input
          value={suiteSizesInput}
          onChange={(event) => setSuiteSizesInput(event.target.value)}
          placeholder="sizes: 1000,5000,10000"
          aria-label="Problem sizes"
        />
        <input
          type="number"
          min={1}
          value={runIterations}
          onChange={(event) => setRunIterations(Math.max(1, Number(event.target.value) || 1))}
          placeholder="iterations"
          aria-label="Measured iterations"
        />
        <input
          type="number"
          min={0}
          value={warmupIterations}
          onChange={(event) => setWarmupIterations(Math.max(0, Number(event.target.value) || 0))}
          placeholder="warmup"
          aria-label="Warmup iterations"
        />
        <input
          type="number"
          min={1000}
          step={1000}
          value={runTimeoutMs}
          onChange={(event) => setRunTimeoutMs(Math.max(1000, Number(event.target.value) || 1000))}
          placeholder="timeout ms"
          aria-label="Timeout milliseconds"
        />
        <button onClick={runSelectedTemplate} disabled={busy}>
          <CirclePlay size={18} />
          <span>Run selected</span>
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
    return <section className="empty-chart">No benchmark points yet. Run a selected algorithm or launch runs from the API.</section>;
  }

  const width = 980;
  const height = 430;
  const padding = { top: 28, right: 34, bottom: 58, left: 76 };
  const minX = Math.min(...allPoints.map((point) => point.datasetSize));
  const maxX = Math.max(...allPoints.map((point) => point.datasetSize));
  const maxY = Math.max(...allPoints.map((point) => point.avg), 1);
  const plotWidth = width - padding.left - padding.right;
  const xScale = (value: number) => {
    if (minX === maxX) {
      return padding.left + plotWidth / 2;
    }
    return padding.left + ((value - minX) / (maxX - minX)) * plotWidth;
  };
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
