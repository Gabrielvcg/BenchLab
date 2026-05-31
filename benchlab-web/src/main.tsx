import React from 'react';
import { createRoot } from 'react-dom/client';
import { Activity, BarChart3, CirclePlay, LogOut, Plus, RefreshCw, ShieldCheck, Trash2 } from 'lucide-react';
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
        "import os\nn=int(os.getenv('BENCHLAB_DATASET_SIZE','1000'))\nseed=int(os.getenv('BENCHLAB_DATASET_SEED','42')) & 0xffffffff\nacc=seed | 1\nfor i in range(n):\n    x=(seed + i * 1103515245 + 12345) & 0xffffffff\n    for j in range(n):\n        x=(x * 1664525 + 1013904223 + j) & 0xffffffff\n        acc ^= (x + i + j) & 1\nprint(acc)",
      JAVA:
        'public class Main { public static void main(String[] args) { int n = Integer.parseInt(System.getenv().getOrDefault("BENCHLAB_DATASET_SIZE", "1000")); long seed = Long.parseLong(System.getenv().getOrDefault("BENCHLAB_DATASET_SEED", "42")) & 0xffffffffL; long acc = seed | 1L; for (int i = 0; i < n; i++) { long x = (seed + (long) i * 1103515245L + 12345L) & 0xffffffffL; for (int j = 0; j < n; j++) { x = (x * 1664525L + 1013904223L + j) & 0xffffffffL; acc ^= (x + i + j) & 1L; } } System.out.println(acc); } }',
      C: '#include <stdint.h>\n#include <stdio.h>\n#include <stdlib.h>\nint main(){ int n = atoi(getenv("BENCHLAB_DATASET_SIZE")); uint32_t seed = (uint32_t)strtoul(getenv("BENCHLAB_DATASET_SEED"), NULL, 10); uint32_t acc = seed | 1u; for(int i=0;i<n;i++){ uint32_t x = seed + (uint32_t)i * 1103515245u + 12345u; for(int j=0;j<n;j++){ x = x * 1664525u + 1013904223u + (uint32_t)j; acc ^= (x + (uint32_t)i + (uint32_t)j) & 1u; } } printf("%u\\n", acc); return 0; }',
      GO: 'package main\n\nimport (\n\t"fmt"\n\t"os"\n\t"strconv"\n)\n\nfunc main() {\n\tn, _ := strconv.Atoi(os.Getenv("BENCHLAB_DATASET_SIZE"))\n\tseed64, _ := strconv.ParseUint(os.Getenv("BENCHLAB_DATASET_SEED"), 10, 32)\n\tseed := uint32(seed64)\n\tacc := seed | 1\n\tfor i := 0; i < n; i++ {\n\t\tx := seed + uint32(i)*1103515245 + 12345\n\t\tfor j := 0; j < n; j++ {\n\t\t\tx = x*1664525 + 1013904223 + uint32(j)\n\t\t\tacc ^= (x + uint32(i) + uint32(j)) & 1\n\t\t}\n\t}\n\tfmt.Println(acc)\n}',
      RUBY: "n = ENV.fetch('BENCHLAB_DATASET_SIZE', '1000').to_i\nseed = ENV.fetch('BENCHLAB_DATASET_SEED', '42').to_i & 0xffffffff\nacc = seed | 1\n(0...n).each do |i|\n  x = (seed + i * 1103515245 + 12345) & 0xffffffff\n  (0...n).each do |j|\n    x = (x * 1664525 + 1013904223 + j) & 0xffffffff\n    acc ^= (x + i + j) & 1\n  end\nend\nputs acc",
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

function shuffleInPlace<T>(items: T[]): void {
  for (let i = items.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    [items[i], items[j]] = [items[j], items[i]];
  }
}

function sanitizeIntegerInput(rawValue: string, min: number, fallback: number): number {
  const parsed = Number.parseInt(rawValue.replace(/[^\d-]/g, ''), 10);
  if (!Number.isFinite(parsed)) {
    return fallback;
  }
  return Math.max(min, parsed);
}

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
  const [problemSizes, setProblemSizes] = React.useState<string[]>(
    (BENCHMARK_SUITE[2]?.datasetSizes ?? BENCHMARK_SUITE[0].datasetSizes).map(value => String(value)),
  );
  const [runIterations, setRunIterations] = React.useState('5');
  const [warmupIterations, setWarmupIterations] = React.useState('1');
  const [runTimeoutMs, setRunTimeoutMs] = React.useState('60000');
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
    setProblemSizes(selectedTemplate.datasetSizes.map(value => String(value)));
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
      const normalizedSizes = problemSizes.map(value => sanitizeIntegerInput(value, 1, 1));
      const uniqueSizes = Array.from(new Set(normalizedSizes));
      if (uniqueSizes.length === 0) {
        throw new Error('Sizes must contain at least one positive integer');
      }
      const normalizedIterations = sanitizeIntegerInput(runIterations, 1, 1);
      const normalizedWarmups = sanitizeIntegerInput(warmupIterations, 0, 0);
      const normalizedTimeoutMs = sanitizeIntegerInput(runTimeoutMs, 1000, 1000);

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

      const runQueue = implementations.flatMap(implementation =>
        datasets.map(dataset => ({
          implementationId: implementation.id,
          datasetId: dataset.id,
          timeoutMs: normalizedTimeoutMs,
          memoryMb: 256,
          cpuLimit: 1,
          iterations: normalizedIterations,
          warmupIterations: normalizedWarmups,
        })),
      );
      shuffleInPlace(runQueue);

      for (const runRequest of runQueue) {
        await api('/api/runs', {
          method: 'POST',
          body: JSON.stringify(runRequest),
        });
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
        <section className="control-panel">
          <h2 className="control-title">Run Configuration</h2>
          <label className="control-label">
            <span className="field-title">Chart algorithm</span>
            <span className="field-help">Algorithm used to display the complexity chart.</span>
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
          </label>
          <label className="control-label">
            <span className="field-title">Algorithm to run</span>
            <span className="field-help">Template that will be executed when you run a new benchmark.</span>
            <select value={selectedTemplateKey} onChange={(event) => setSelectedTemplateKey(event.target.value)} aria-label="Algorithm template">
              {BENCHMARK_SUITE.map((template) => (
                <option key={template.key} value={template.key}>
                  {template.name} ({template.complexityDeclared})
                </option>
              ))}
            </select>
          </label>
          <div className="control-label">
            <span className="field-title">Problem sizes</span>
            <span className="field-help">Each row is a separate input size. Add, edit, or remove values individually.</span>
            <div className="sizes-list">
              {problemSizes.map((size, index) => (
                <div key={`${index}-${size}`} className="size-row">
                  <span className="size-tag">N{index + 1}</span>
                  <input
                    type="text"
                    inputMode="numeric"
                    value={size}
                    onChange={(event) => {
                      const next = [...problemSizes];
                      next[index] = event.target.value;
                      setProblemSizes(next);
                    }}
                    onBlur={() => {
                      const next = [...problemSizes];
                      next[index] = String(sanitizeIntegerInput(next[index], 1, 1));
                      setProblemSizes(next);
                    }}
                    aria-label={`Problem size ${index + 1}`}
                  />
                  <button
                    type="button"
                    className="icon-button"
                    onClick={() => setProblemSizes(problemSizes.filter((_, i) => i !== index))}
                    aria-label={`Remove size ${index + 1}`}
                    disabled={problemSizes.length <= 1}
                  >
                    <Trash2 size={16} />
                  </button>
                </div>
              ))}
            </div>
            <div className="size-actions">
              <button type="button" className="inline-button" onClick={() => setProblemSizes([...problemSizes, problemSizes[problemSizes.length - 1] ?? '1000'])}>
                <Plus size={16} />
                <span>Add size</span>
              </button>
              <button type="button" className="inline-button secondary" onClick={() => setProblemSizes(selectedTemplate.datasetSizes.map(value => String(value)))}>
                <RefreshCw size={16} />
                <span>Use defaults</span>
              </button>
            </div>
          </div>
          <label className="control-label">
            <span className="field-title">Measured iterations</span>
            <span className="field-help">How many times each input size is measured and averaged.</span>
            <input
              type="text"
              inputMode="numeric"
              value={runIterations}
              onChange={(event) => setRunIterations(event.target.value)}
              onBlur={() => setRunIterations(String(sanitizeIntegerInput(runIterations, 1, 1)))}
              aria-label="Measured iterations"
            />
          </label>
          <label className="control-label">
            <span className="field-title">Warmup iterations</span>
            <span className="field-help">Runs before measuring to stabilize performance.</span>
            <input
              type="text"
              inputMode="numeric"
              value={warmupIterations}
              onChange={(event) => setWarmupIterations(event.target.value)}
              onBlur={() => setWarmupIterations(String(sanitizeIntegerInput(warmupIterations, 0, 0)))}
              aria-label="Warmup iterations"
            />
          </label>
          <label className="control-label">
            <span className="field-title">Timeout (ms)</span>
            <span className="field-help">Maximum time allowed for one benchmark run.</span>
            <input
              type="text"
              inputMode="numeric"
              value={runTimeoutMs}
              onChange={(event) => setRunTimeoutMs(event.target.value)}
              onBlur={() => setRunTimeoutMs(String(sanitizeIntegerInput(runTimeoutMs, 1000, 1000)))}
              aria-label="Timeout milliseconds"
            />
          </label>
        </section>
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
  const [sortBy, setSortBy] = React.useState<'id' | 'status' | 'algorithmName' | 'language' | 'datasetSize' | 'wallTimeMs'>('id');
  const [sortDirection, setSortDirection] = React.useState<'asc' | 'desc'>('desc');

  const sortedRuns = React.useMemo(() => {
    const direction = sortDirection === 'asc' ? 1 : -1;
    const sorted = [...runs];
    sorted.sort((left, right) => {
      const numberCompare = (a: number | null | undefined, b: number | null | undefined) => (a ?? -1) - (b ?? -1);

      if (sortBy === 'id') return numberCompare(left.id, right.id) * direction;
      if (sortBy === 'datasetSize') return numberCompare(left.datasetSize, right.datasetSize) * direction;
      if (sortBy === 'wallTimeMs') return numberCompare(left.wallTimeMs, right.wallTimeMs) * direction;
      if (sortBy === 'status') return left.status.localeCompare(right.status) * direction;
      if (sortBy === 'algorithmName') return left.algorithmName.localeCompare(right.algorithmName) * direction;
      return left.language.localeCompare(right.language) * direction;
    });
    return sorted;
  }, [runs, sortBy, sortDirection]);

  function updateSort(nextSortBy: 'id' | 'status' | 'algorithmName' | 'language' | 'datasetSize' | 'wallTimeMs') {
    if (sortBy === nextSortBy) {
      setSortDirection(current => (current === 'asc' ? 'desc' : 'asc'));
      return;
    }
    setSortBy(nextSortBy);
    setSortDirection('asc');
  }

  return (
    <section className="table-surface">
      <h2>Recent runs</h2>
      <div className="sort-controls" role="group" aria-label="Sort runs table">
        <button type="button" className="sort-chip" onClick={() => updateSort('id')} aria-pressed={sortBy === 'id'}>
          ID {sortBy === 'id' ? (sortDirection === 'asc' ? '↑' : '↓') : ''}
        </button>
        <button type="button" className="sort-chip" onClick={() => updateSort('status')} aria-pressed={sortBy === 'status'}>
          Status {sortBy === 'status' ? (sortDirection === 'asc' ? '↑' : '↓') : ''}
        </button>
        <button
          type="button"
          className="sort-chip"
          onClick={() => updateSort('algorithmName')}
          aria-pressed={sortBy === 'algorithmName'}
        >
          Algorithm {sortBy === 'algorithmName' ? (sortDirection === 'asc' ? '↑' : '↓') : ''}
        </button>
        <button type="button" className="sort-chip" onClick={() => updateSort('language')} aria-pressed={sortBy === 'language'}>
          Language {sortBy === 'language' ? (sortDirection === 'asc' ? '↑' : '↓') : ''}
        </button>
        <button type="button" className="sort-chip" onClick={() => updateSort('datasetSize')} aria-pressed={sortBy === 'datasetSize'}>
          Size {sortBy === 'datasetSize' ? (sortDirection === 'asc' ? '↑' : '↓') : ''}
        </button>
        <button type="button" className="sort-chip" onClick={() => updateSort('wallTimeMs')} aria-pressed={sortBy === 'wallTimeMs'}>
          Wall ms {sortBy === 'wallTimeMs' ? (sortDirection === 'asc' ? '↑' : '↓') : ''}
        </button>
      </div>
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
            {sortedRuns.map((run) => (
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
