import React from 'react';
import { createRoot } from 'react-dom/client';
import { Activity, BarChart3, CirclePlay, LogOut, RefreshCw, RotateCcw, ShieldCheck, ZoomIn, ZoomOut } from 'lucide-react';
import { describeApiError, estimateDockerInvocations, hasActiveRuns, mapWithConcurrency, summarizeRuns } from './app-logic';
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

type ChartScale = 'linear' | 'log';
type ChartHoverPoint = {
  language: string;
  point: ComplexityPoint;
  color: string;
  x: number;
  y: number;
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
  orchestrationWallTimeMs: number | null;
};

type AuthResponse = {
  id_token: string;
};

type ImplementationLanguage = 'PYTHON' | 'JAVA' | 'C' | 'GO' | 'RUBY' | 'RUST' | 'ASSEMBLY';

type AlgorithmTemplate = {
  key: string;
  name: string;
  category: string;
  complexityDeclared: string;
  quickDatasetSizes: number[];
  datasetSizes: number[];
  sources: Partial<Record<ImplementationLanguage, string>>;
};

const BENCHMARK_SUITE: AlgorithmTemplate[] = [
  {
    key: 'constant-read',
    name: 'constant-read',
    category: 'synthetic',
    complexityDeclared: 'O(1)',
    quickDatasetSizes: [100000, 500000, 1000000],
    datasetSizes: [1000000, 5000000, 10000000, 25000000],
    sources: {
      PYTHON:
        "import os\nn=int(os.getenv('BENCHLAB_DATASET_SIZE','1000'))\na=n%97\nb=(n*3)%101\nprint((a+b)%997)",
      JAVA:
        'public class Main { public static void main(String[] args) { long n = Long.parseLong(System.getenv().getOrDefault("BENCHLAB_DATASET_SIZE", "1000")); long a = n % 97; long b = (n * 3) % 101; System.out.println((a + b) % 997); } }',
      C: '#include <stdio.h>\n#include <stdlib.h>\nint main(){ long n = atol(getenv("BENCHLAB_DATASET_SIZE")); long a = n % 97; long b = (n * 3) % 101; printf("%ld\\n", (a + b) % 997); return 0; }',
      GO: 'package main\n\nimport (\n\t"fmt"\n\t"os"\n\t"strconv"\n)\n\nfunc main() {\n\tn, _ := strconv.ParseInt(os.Getenv("BENCHLAB_DATASET_SIZE"), 10, 64)\n\ta := n % 97\n\tb := (n * 3) % 101\n\tfmt.Println((a + b) % 997)\n}',
      RUBY: "n = ENV.fetch('BENCHLAB_DATASET_SIZE', '1000').to_i\na = n % 97\nb = (n * 3) % 101\nputs((a + b) % 997)",
      RUST:
        'use std::env;\nfn main() {\n    let n: i64 = env::var("BENCHLAB_DATASET_SIZE").unwrap_or_else(|_| "1000".into()).parse().unwrap_or(1000);\n    let a = n % 97;\n    let b = (n * 3) % 101;\n    println!("{}", (a + b) % 997);\n}',
      ASSEMBLY:
        '.section .rodata\nenv_name: .string "BENCHLAB_DATASET_SIZE"\nparse_fmt: .string "%ld"\nout_fmt: .string "%ld\\n"\n.section .bss\n.lcomm parsed_n, 8\n.text\n.globl main\n.extern getenv\n.extern sscanf\n.extern printf\nmain:\npush %rbp\nmov %rsp, %rbp\nsub $16, %rsp\nlea env_name(%rip), %rdi\ncall getenv\ntest %rax, %rax\njne .parse\nmovq $1000, parsed_n(%rip)\njmp .compute\n.parse:\nmov %rax, %rdi\nlea parse_fmt(%rip), %rsi\nlea parsed_n(%rip), %rdx\nxor %eax, %eax\ncall sscanf\n.compute:\nmov parsed_n(%rip), %rax\nmov %rax, %rbx\nmov $97, %rcx\ncqo\nidiv %rcx\nmov %rdx, %r8\nmov parsed_n(%rip), %rax\nimul $3, %rax, %rax\nmov $101, %rcx\ncqo\nidiv %rcx\nadd %rdx, %r8\nmov %r8, %rax\nmov $997, %rcx\ncqo\nidiv %rcx\nlea out_fmt(%rip), %rdi\nmov %rdx, %rsi\nxor %eax, %eax\ncall printf\nxor %eax, %eax\nleave\nret',
    },
  },
  {
    key: 'log-halving',
    name: 'log-halving',
    category: 'synthetic',
    complexityDeclared: 'O(log n)',
    quickDatasetSizes: [100000, 500000, 1000000],
    datasetSizes: [1000000, 5000000, 10000000, 25000000],
    sources: {
      PYTHON:
        "import os\nn=int(os.getenv('BENCHLAB_DATASET_SIZE','1000'))\nsteps=0\nwhile n>1:\n    n//=2\n    steps+=1\nprint(steps)",
      JAVA:
        'public class Main { public static void main(String[] args) { long n = Long.parseLong(System.getenv().getOrDefault("BENCHLAB_DATASET_SIZE", "1000")); long steps = 0; while (n > 1) { n /= 2; steps++; } System.out.println(steps); } }',
      C: '#include <stdio.h>\n#include <stdlib.h>\nint main(){ long n = atol(getenv("BENCHLAB_DATASET_SIZE")); long steps = 0; while(n > 1){ n /= 2; steps++; } printf("%ld\\n", steps); return 0; }',
      GO: 'package main\n\nimport (\n\t"fmt"\n\t"os"\n\t"strconv"\n)\n\nfunc main() {\n\tn, _ := strconv.ParseInt(os.Getenv("BENCHLAB_DATASET_SIZE"), 10, 64)\n\tvar steps int64\n\tfor n > 1 {\n\t\tn /= 2\n\t\tsteps++\n\t}\n\tfmt.Println(steps)\n}',
      RUBY: "n = ENV.fetch('BENCHLAB_DATASET_SIZE', '1000').to_i\nsteps = 0\nwhile n > 1\n  n /= 2\n  steps += 1\nend\nputs steps",
      RUST:
        'use std::env;\nfn main() {\n    let mut n: i64 = env::var("BENCHLAB_DATASET_SIZE").unwrap_or_else(|_| "1000".into()).parse().unwrap_or(1000);\n    let mut steps: i64 = 0;\n    while n > 1 { n /= 2; steps += 1; }\n    println!("{}", steps);\n}',
    },
  },
  {
    key: 'linear-sum',
    name: 'linear-sum',
    category: 'synthetic',
    complexityDeclared: 'O(n)',
    quickDatasetSizes: [100000, 500000, 1000000],
    datasetSizes: [1000000, 5000000, 10000000, 25000000],
    sources: {
      PYTHON:
        "import os\nn=int(os.getenv('BENCHLAB_DATASET_SIZE','1000'))\ns=0\nfor i in range(n):\n    s += (i % 97)\nprint(s)",
      JAVA:
        'public class Main { public static void main(String[] args) { long n = Long.parseLong(System.getenv().getOrDefault("BENCHLAB_DATASET_SIZE", "1000")); long s = 0; for (long i = 0; i < n; i++) { s += i % 97; } System.out.println(s); } }',
      C: '#include <stdio.h>\n#include <stdlib.h>\nint main(){ long n = atol(getenv("BENCHLAB_DATASET_SIZE")); long s = 0; for(long i=0;i<n;i++){ s += i % 97; } printf("%ld\\n", s); return 0; }',
      GO: 'package main\n\nimport (\n\t"fmt"\n\t"os"\n\t"strconv"\n)\n\nfunc main() {\n\tn, _ := strconv.ParseInt(os.Getenv("BENCHLAB_DATASET_SIZE"), 10, 64)\n\tvar s int64\n\tfor i := int64(0); i < n; i++ {\n\t\ts += i % 97\n\t}\n\tfmt.Println(s)\n}',
      RUBY: "n = ENV.fetch('BENCHLAB_DATASET_SIZE', '1000').to_i\ns = 0\n(0...n).each do |i|\n  s += i % 97\nend\nputs s",
      RUST:
        'use std::env;\nfn main() {\n    let n: i64 = env::var("BENCHLAB_DATASET_SIZE").unwrap_or_else(|_| "1000".into()).parse().unwrap_or(1000);\n    let mut s: i64 = 0;\n    for i in 0..n { s += i % 97; }\n    println!("{}", s);\n}',
      ASSEMBLY:
        '.section .rodata\nenv_name: .string "BENCHLAB_DATASET_SIZE"\nparse_fmt: .string "%ld"\nout_fmt: .string "%ld\\n"\n.section .bss\n.lcomm parsed_n, 8\n.text\n.globl main\n.extern getenv\n.extern sscanf\n.extern printf\nmain:\npush %rbp\nmov %rsp, %rbp\npush %rbx\npush %r12\npush %r13\nsub $8, %rsp\nlea env_name(%rip), %rdi\ncall getenv\ntest %rax, %rax\njne .parse\nmovq $1000, parsed_n(%rip)\njmp .start\n.parse:\nmov %rax, %rdi\nlea parse_fmt(%rip), %rsi\nlea parsed_n(%rip), %rdx\nxor %eax, %eax\ncall sscanf\n.start:\nmov parsed_n(%rip), %r12\nxor %rbx, %rbx\nxor %r13, %r13\n.loop:\ncmp %r12, %rbx\njge .done\nmov %rbx, %rax\nmov $97, %rcx\ncqo\nidiv %rcx\nadd %rdx, %r13\ninc %rbx\njmp .loop\n.done:\nlea out_fmt(%rip), %rdi\nmov %r13, %rsi\nxor %eax, %eax\ncall printf\nadd $8, %rsp\npop %r13\npop %r12\npop %rbx\nxor %eax, %eax\nleave\nret',
    },
  },
  {
    key: 'linearithmic-mix',
    name: 'linearithmic-mix',
    category: 'synthetic',
    complexityDeclared: 'O(n log n)',
    quickDatasetSizes: [10000, 50000, 100000],
    datasetSizes: [100000, 250000, 500000, 1000000],
    sources: {
      PYTHON:
        "import os\nn=int(os.getenv('BENCHLAB_DATASET_SIZE','1000'))\ns=0\nfor i in range(1,n+1):\n    x=i\n    while x>1:\n        x//=2\n        s += x & 1\nprint(s)",
      JAVA:
        'public class Main { public static void main(String[] args) { long n = Long.parseLong(System.getenv().getOrDefault("BENCHLAB_DATASET_SIZE", "1000")); long s = 0; for (long i = 1; i <= n; i++) { long x = i; while (x > 1) { x /= 2; s += x & 1; } } System.out.println(s); } }',
      C: '#include <stdio.h>\n#include <stdlib.h>\nint main(){ long n = atol(getenv("BENCHLAB_DATASET_SIZE")); long s = 0; for(long i=1;i<=n;i++){ long x=i; while(x>1){ x/=2; s += x & 1; } } printf("%ld\\n", s); return 0; }',
      GO: 'package main\n\nimport (\n\t"fmt"\n\t"os"\n\t"strconv"\n)\n\nfunc main() {\n\tn, _ := strconv.ParseInt(os.Getenv("BENCHLAB_DATASET_SIZE"), 10, 64)\n\tvar s int64\n\tfor i := int64(1); i <= n; i++ {\n\t\tx := i\n\t\tfor x > 1 {\n\t\t\tx /= 2\n\t\t\ts += x & 1\n\t\t}\n\t}\n\tfmt.Println(s)\n}',
      RUBY: "n = ENV.fetch('BENCHLAB_DATASET_SIZE', '1000').to_i\ns = 0\n(1..n).each do |i|\n  x = i\n  while x > 1\n    x /= 2\n    s += (x & 1)\n  end\nend\nputs s",
      RUST:
        'use std::env;\nfn main() {\n    let n: i64 = env::var("BENCHLAB_DATASET_SIZE").unwrap_or_else(|_| "1000".into()).parse().unwrap_or(1000);\n    let mut s: i64 = 0;\n    for i in 1..=n {\n        let mut x = i;\n        while x > 1 { x /= 2; s += x & 1; }\n    }\n    println!("{}", s);\n}',
    },
  },
  {
    key: 'quadratic-nested',
    name: 'quadratic-nested',
    category: 'synthetic',
    complexityDeclared: 'O(n^2)',
    quickDatasetSizes: [500, 1000, 2000],
    datasetSizes: [2000, 4000, 6000, 8000],
    sources: {
      PYTHON:
        "import os\nn=int(os.getenv('BENCHLAB_DATASET_SIZE','1000'))\nseed=int(os.getenv('BENCHLAB_DATASET_SEED','42')) & 0xffffffff\nacc=seed | 1\nfor i in range(n):\n    x=(seed + i * 1103515245 + 12345) & 0xffffffff\n    for j in range(n):\n        x=(x * 1664525 + 1013904223 + j) & 0xffffffff\n        acc ^= (x + i + j) & 1\nprint(acc)",
      JAVA:
        'public class Main { public static void main(String[] args) { int n = Integer.parseInt(System.getenv().getOrDefault("BENCHLAB_DATASET_SIZE", "1000")); long seed = Long.parseLong(System.getenv().getOrDefault("BENCHLAB_DATASET_SEED", "42")) & 0xffffffffL; long acc = seed | 1L; for (int i = 0; i < n; i++) { long x = (seed + (long) i * 1103515245L + 12345L) & 0xffffffffL; for (int j = 0; j < n; j++) { x = (x * 1664525L + 1013904223L + j) & 0xffffffffL; acc ^= (x + i + j) & 1L; } } System.out.println(acc); } }',
      C: '#include <stdint.h>\n#include <stdio.h>\n#include <stdlib.h>\nint main(){ int n = atoi(getenv("BENCHLAB_DATASET_SIZE")); uint32_t seed = (uint32_t)strtoul(getenv("BENCHLAB_DATASET_SEED"), NULL, 10); uint32_t acc = seed | 1u; for(int i=0;i<n;i++){ uint32_t x = seed + (uint32_t)i * 1103515245u + 12345u; for(int j=0;j<n;j++){ x = x * 1664525u + 1013904223u + (uint32_t)j; acc ^= (x + (uint32_t)i + (uint32_t)j) & 1u; } } printf("%u\\n", acc); return 0; }',
      GO: 'package main\n\nimport (\n\t"fmt"\n\t"os"\n\t"strconv"\n)\n\nfunc main() {\n\tn, _ := strconv.Atoi(os.Getenv("BENCHLAB_DATASET_SIZE"))\n\tseed64, _ := strconv.ParseUint(os.Getenv("BENCHLAB_DATASET_SEED"), 10, 32)\n\tseed := uint32(seed64)\n\tacc := seed | 1\n\tfor i := 0; i < n; i++ {\n\t\tx := seed + uint32(i)*1103515245 + 12345\n\t\tfor j := 0; j < n; j++ {\n\t\t\tx = x*1664525 + 1013904223 + uint32(j)\n\t\t\tacc ^= (x + uint32(i) + uint32(j)) & 1\n\t\t}\n\t}\n\tfmt.Println(acc)\n}',
      RUBY: "n = ENV.fetch('BENCHLAB_DATASET_SIZE', '1000').to_i\nseed = ENV.fetch('BENCHLAB_DATASET_SEED', '42').to_i & 0xffffffff\nacc = seed | 1\n(0...n).each do |i|\n  x = (seed + i * 1103515245 + 12345) & 0xffffffff\n  (0...n).each do |j|\n    x = (x * 1664525 + 1013904223 + j) & 0xffffffff\n    acc ^= (x + i + j) & 1\n  end\nend\nputs acc",
      RUST:
        'use std::env;\nfn main() {\n    let n: u32 = env::var("BENCHLAB_DATASET_SIZE").unwrap_or_else(|_| "1000".into()).parse().unwrap_or(1000);\n    let seed: u32 = env::var("BENCHLAB_DATASET_SEED").unwrap_or_else(|_| "42".into()).parse().unwrap_or(42);\n    let mut acc: u32 = seed | 1;\n    for i in 0..n {\n        let mut x = seed.wrapping_add(i.wrapping_mul(1103515245)).wrapping_add(12345);\n        for j in 0..n {\n            x = x.wrapping_mul(1664525).wrapping_add(1013904223).wrapping_add(j);\n            acc ^= (x.wrapping_add(i).wrapping_add(j)) & 1;\n        }\n    }\n    println!("{}", acc);\n}',
    },
  },
];

const languageColors: Record<string, string> = {
  C: '#00843d',
  JAVA: '#d9482f',
  PYTHON: '#2563eb',
  GO: '#0099b8',
  RUBY: '#c2185b',
  RUST: '#f47c20',
  ASSEMBLY: '#111827',
  CPP: '#7c3aed',
};

const AVAILABLE_LANGUAGES: ImplementationLanguage[] = ['PYTHON', 'JAVA', 'C', 'GO', 'RUBY', 'RUST', 'ASSEMBLY'];
const QUICK_DEMO_LANGUAGES: ImplementationLanguage[] = ['PYTHON', 'JAVA', 'C'];
type DemoPreset = 'quick' | 'broad';

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

function parseProblemSizesInput(rawValue: string): number[] {
  return Array.from(
    new Set(
      rawValue
        .split(/[\s,;]+/)
        .map(item => sanitizeIntegerInput(item, 1, Number.NaN))
        .filter(value => Number.isFinite(value) && value > 0),
    ),
  );
}

function formatDatasetTick(value: number): string {
  return value.toLocaleString('es-ES');
}

function formatDurationTick(value: number): string {
  if (value >= 1000) {
    return `${(value / 1000).toLocaleString('es-ES', { maximumFractionDigits: 1 })}s`;
  }
  return `${Math.round(value).toLocaleString('es-ES')}ms`;
}

function linearTicks(maxValue: number): number[] {
  return Array.from(new Set([0, 0.25, 0.5, 0.75, 1].map(step => Math.round(maxValue * step))));
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
  const [isAuthenticating, setIsAuthenticating] = React.useState(false);
  const [isSubmitting, setIsSubmitting] = React.useState(false);
  const [isRefreshing, setIsRefreshing] = React.useState(false);
  const [submissionProgress, setSubmissionProgress] = React.useState<{ completed: number; total: number } | null>(null);
  const [demoPreset, setDemoPreset] = React.useState<DemoPreset>('quick');
  const [selectedTemplateKey, setSelectedTemplateKey] = React.useState(BENCHMARK_SUITE[2]?.key ?? BENCHMARK_SUITE[0].key);
  const [problemSizesInput, setProblemSizesInput] = React.useState(
    (BENCHMARK_SUITE[2]?.quickDatasetSizes ?? BENCHMARK_SUITE[0].quickDatasetSizes).join('\n'),
  );
  const [selectedLanguages, setSelectedLanguages] = React.useState<ImplementationLanguage[]>(QUICK_DEMO_LANGUAGES);
  const [runIterations, setRunIterations] = React.useState('2');
  const [warmupIterations, setWarmupIterations] = React.useState('0');
  const [runTimeoutMs, setRunTimeoutMs] = React.useState('15000');
  const selectedAlgorithmIdRef = React.useRef<number | null>(selectedAlgorithmId);
  const refreshInFlightRef = React.useRef(false);
  const refreshAbortRef = React.useRef<AbortController | null>(null);
  const refreshSequenceRef = React.useRef(0);

  const authHeaders = React.useMemo(() => ({ Authorization: `Bearer ${token}` }), [token]);
  const selectedTemplate = React.useMemo(
    () => BENCHMARK_SUITE.find((item) => item.key === selectedTemplateKey) ?? BENCHMARK_SUITE[0],
    [selectedTemplateKey],
  );
  const templateLanguages = React.useMemo(
    () => AVAILABLE_LANGUAGES.filter(language => Boolean(selectedTemplate.sources[language])),
    [selectedTemplate],
  );

  React.useEffect(() => {
    selectedAlgorithmIdRef.current = selectedAlgorithmId;
  }, [selectedAlgorithmId]);

  React.useEffect(() => {
    if (demoPreset === 'quick') {
      setProblemSizesInput(selectedTemplate.quickDatasetSizes.join('\n'));
      const quickLanguages = QUICK_DEMO_LANGUAGES.filter(language => templateLanguages.includes(language));
      setSelectedLanguages(quickLanguages.length > 0 ? quickLanguages : templateLanguages.slice(0, 3));
      setRunIterations('2');
      setWarmupIterations('0');
      setRunTimeoutMs('15000');
      return;
    }
    setProblemSizesInput(selectedTemplate.datasetSizes.join('\n'));
    setSelectedLanguages([...templateLanguages]);
    setRunIterations('5');
    setWarmupIterations('1');
    setRunTimeoutMs('30000');
  }, [demoPreset, selectedTemplate, templateLanguages]);

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
      const rawBody = await response.text();
      throw new Error(describeApiError(response.status, response.statusText, rawBody));
    }
    if (response.status === 204) {
      return undefined as T;
    }
    return response.json() as Promise<T>;
  }

  async function signIn(event: React.FormEvent) {
    event.preventDefault();
    setIsAuthenticating(true);
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
      setIsAuthenticating(false);
    }
  }

  async function refresh(nextAlgorithmId?: number | null, background = false) {
    if (!token) return;
    if (background && refreshInFlightRef.current) return;
    refreshAbortRef.current?.abort();
    const controller = new AbortController();
    refreshAbortRef.current = controller;
    refreshInFlightRef.current = true;
    const sequence = refreshSequenceRef.current + 1;
    refreshSequenceRef.current = sequence;
    if (!background) setIsRefreshing(true);
    try {
      const preferredAlgorithmId = nextAlgorithmId ?? selectedAlgorithmIdRef.current;
      const [algorithmData, runData, preferredComplexity] = await Promise.all([
        api<Algorithm[]>('/api/algorithms', { signal: controller.signal }),
        api<RunSummary[]>('/api/runs', { signal: controller.signal }),
        preferredAlgorithmId
          ? api<ComplexityResponse>(
              `/api/benchmarks/complexity?algorithmId=${preferredAlgorithmId}&metric=orchestrationWallTimeMs`,
              { signal: controller.signal },
            )
          : Promise.resolve(null),
      ]);
      if (sequence !== refreshSequenceRef.current) return;
      setAlgorithms(algorithmData);
      setRuns(runData);
      const currentAlgorithmId = selectedAlgorithmIdRef.current;
      const currentStillExists = currentAlgorithmId != null && algorithmData.some((algorithm) => algorithm.id === currentAlgorithmId);
      const latestRunAlgorithmId = runData.find((run) => algorithmData.some((algorithm) => algorithm.id === run.algorithmId))?.algorithmId ?? null;
      const algorithmId = nextAlgorithmId ?? (currentStillExists ? currentAlgorithmId : latestRunAlgorithmId) ?? algorithmData[0]?.id ?? null;
      setSelectedAlgorithmId(algorithmId);
      if (algorithmId) {
        const complexityData =
          preferredAlgorithmId === algorithmId && preferredComplexity
            ? preferredComplexity
            : await api<ComplexityResponse>(
                `/api/benchmarks/complexity?algorithmId=${algorithmId}&metric=orchestrationWallTimeMs`,
                { signal: controller.signal },
              );
        if (sequence !== refreshSequenceRef.current) return;
        setComplexity(complexityData);
      } else {
        setComplexity(null);
      }
      if (!background) setMessage('Data refreshed');
    } catch (error) {
      if ((error as Error).name === 'AbortError') return;
      setMessage(`Refresh failed: ${(error as Error).message}`);
    } finally {
      if (sequence === refreshSequenceRef.current) {
        refreshInFlightRef.current = false;
        setIsRefreshing(false);
      }
    }
  }

  async function runSelectedTemplate() {
    setIsSubmitting(true);
    setSubmissionProgress(null);
    setMessage(`Seeding algorithm ${selectedTemplate.name}`);
    try {
      const uniqueSizes = parseProblemSizesInput(problemSizesInput);
      if (uniqueSizes.length === 0) {
        throw new Error('Sizes must contain at least one positive integer');
      }
      if (selectedLanguages.length === 0) {
        throw new Error('Select at least one language to run');
      }
      const normalizedIterations = sanitizeIntegerInput(runIterations, 1, 1);
      const normalizedWarmups = sanitizeIntegerInput(warmupIterations, 0, 0);
      const normalizedTimeoutMs = sanitizeIntegerInput(runTimeoutMs, 1000, 1000);

      const suffix = Date.now().toString(36);
      const algorithm = await api<Algorithm>('/api/algorithms', {
        method: 'POST',
        body: JSON.stringify({
          name: `${selectedTemplate.name}-${suffix}`,
          category: selectedTemplate.category,
          version: 'v1',
          complexityDeclared: selectedTemplate.complexityDeclared,
        }),
      });

      const datasets = await mapWithConcurrency(uniqueSizes, 3, size =>
        api<{ id: number }>('/api/datasets', {
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

      const implementations = await Promise.all(
        (Object.entries(selectedTemplate.sources) as Array<[ImplementationLanguage, string]>)
          .filter(([language]) => selectedLanguages.includes(language))
          .map(([language, sourceCode]) =>
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

      setSubmissionProgress({ completed: 0, total: runQueue.length });
      await mapWithConcurrency(
        runQueue,
        4,
        runRequest =>
          api('/api/runs', {
          method: 'POST',
          body: JSON.stringify(runRequest),
          }),
        (completed, total) => {
          setSubmissionProgress({ completed, total });
          setMessage(`Queued ${completed} of ${total} runs`);
        },
      );

      setMessage('Runs queued. Refreshing while worker processes runs');
      await refresh(algorithm.id, true);
    } catch (error) {
      setMessage(`Run failed: ${(error as Error).message}`);
    } finally {
      setIsSubmitting(false);
      setSubmissionProgress(null);
    }
  }

  React.useEffect(() => {
    if (token) {
      refresh();
    }
  }, [token]);

  const activeRuns = hasActiveRuns(runs);
  React.useEffect(() => {
    if (!token || !activeRuns) return undefined;
    const refreshIfVisible = () => {
      if (document.visibilityState === 'visible') refresh(selectedAlgorithmIdRef.current, true);
    };
    const interval = window.setInterval(refreshIfVisible, 3000);
    document.addEventListener('visibilitychange', refreshIfVisible);
    return () => {
      window.clearInterval(interval);
      document.removeEventListener('visibilitychange', refreshIfVisible);
    };
  }, [token, activeRuns]);

  const runProgress = React.useMemo(() => summarizeRuns(runs), [runs]);
  const configuredDatasetCount = parseProblemSizesInput(problemSizesInput).length;
  const estimatedInvocations = estimateDockerInvocations(
    selectedLanguages,
    configuredDatasetCount,
    sanitizeIntegerInput(runIterations, 1, 1),
    sanitizeIntegerInput(warmupIterations, 0, 0),
  );

  if (!token) {
    return (
      <main className="login-shell">
        <form className="login-panel" onSubmit={signIn}>
          <div className="brand-row login-brand">
            <ShieldCheck size={28} />
            <div>
              <span>BenchLab</span>
              <p>Secure access to the benchmark dashboard</p>
            </div>
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
          <button type="submit" disabled={isAuthenticating}>
            <CirclePlay size={18} />
            <span>{isAuthenticating ? 'Signing in…' : 'Sign in'}</span>
          </button>
          {message !== 'Ready' ? <p className="login-message">{message}</p> : null}
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
            <span className="field-title">Demo preset</span>
            <span className="field-help">
              Quick demo is portfolio-friendly. Broader comparison is opt-in and launches substantially more isolated containers.
            </span>
            <select value={demoPreset} onChange={event => setDemoPreset(event.target.value as DemoPreset)} aria-label="Demo preset">
              <option value="quick">Quick demo (recommended)</option>
              <option value="broad">Broader comparison (slower)</option>
            </select>
            <span className="preset-estimate">Estimated isolated container invocations: {estimatedInvocations}</span>
          </label>
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
            <span className="field-title">Languages to run</span>
            <span className="field-help">Choose one or more languages for this run. Assembly support is available through custom/API code.</span>
            <div className="language-grid">
              {templateLanguages.map(language => (
                <label key={language} className="language-option">
                  <input
                    type="checkbox"
                    checked={selectedLanguages.includes(language)}
                    onChange={() =>
                      setSelectedLanguages(current =>
                        current.includes(language) ? current.filter(item => item !== language) : [...current, language],
                      )
                    }
                  />
                  <span>{language}</span>
                </label>
              ))}
            </div>
          </div>
          <div className="control-label">
            <span className="field-title">Problem sizes</span>
            <span className="field-help">Enter multiple values separated by new lines, commas, spaces, or semicolons.</span>
            <textarea
              className="sizes-textarea"
              value={problemSizesInput}
              onChange={event => setProblemSizesInput(event.target.value)}
              placeholder="1000000&#10;5000000&#10;10000000&#10;25000000"
              aria-label="Problem sizes list"
              rows={5}
            />
            <div className="size-actions">
              <button
                type="button"
                className="inline-button secondary"
                onClick={() =>
                  setProblemSizesInput((demoPreset === 'quick' ? selectedTemplate.quickDatasetSizes : selectedTemplate.datasetSizes).join('\n'))
                }
              >
                <RefreshCw size={16} />
                <span>Use preset sizes</span>
              </button>
            </div>
          </div>
          <label className="control-label">
            <span className="field-title">Measured iterations</span>
            <span className="field-help">Fresh isolated executions averaged as orchestration wall time.</span>
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
            <span className="field-help">Unmeasured isolated runs; these do not warm the same process or JVM.</span>
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
            <span className="field-help">Maximum time for each compile or run container invocation.</span>
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
        <button onClick={runSelectedTemplate} disabled={isSubmitting}>
          <CirclePlay size={18} />
          <span>{isSubmitting ? 'Queuing runs…' : 'Run selected'}</span>
        </button>
        <button onClick={() => refresh()} disabled={isRefreshing || isSubmitting}>
          <RefreshCw size={18} />
          <span>{isRefreshing ? 'Refreshing…' : 'Refresh'}</span>
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
        {submissionProgress ? (
          <p className="status-text">Submission: {submissionProgress.completed}/{submissionProgress.total}</p>
        ) : null}
        <p className="status-text" role="status">{message}</p>
      </aside>

      <section className="workspace">
        <header>
          <div>
            <p className="eyebrow">Production laboratory</p>
            <h1>Isolated execution wall time by input size</h1>
          </div>
          <div className="metric-pill">
            <BarChart3 size={18} />
            orchestrationWallTimeMs
          </div>
        </header>
        <section className="progress-strip" aria-label="Recent run progress">
          <span><strong>{runProgress.queued}</strong> queued</span>
          <span><strong>{runProgress.running}</strong> running</span>
          <span><strong>{runProgress.completed}</strong> completed</span>
          <span><strong>{runProgress.failed}</strong> failed</span>
          {activeRuns ? <span className="live-indicator">Live updates</span> : <span>Idle</span>}
        </section>
        <ComplexityChart complexity={complexity} />
        <RunTable runs={runs} />
      </section>
    </main>
  );
}

function ComplexityChart({ complexity }: { complexity: ComplexityResponse | null }) {
  const orderedSeries = [...(complexity?.series ?? [])].sort((left, right) => left.language.localeCompare(right.language));
  const allPoints = complexity?.series.flatMap((series) => series.points) ?? [];
  const [selectedChartLanguages, setSelectedChartLanguages] = React.useState<string[]>(orderedSeries.map(series => series.language));
  const closestPairs = React.useMemo(() => {
    const candidates: Array<{ left: ComplexitySeries; right: ComplexitySeries; score: number }> = [];
    for (let i = 0; i < orderedSeries.length; i += 1) {
      for (let j = i + 1; j < orderedSeries.length; j += 1) {
        const left = orderedSeries[i];
        const right = orderedSeries[j];
        const rightBySize = new Map(right.points.map(point => [point.datasetSize, point.avg]));
        const overlap = left.points
          .filter(point => rightBySize.has(point.datasetSize))
          .map(point => {
            const rightAvg = rightBySize.get(point.datasetSize) ?? point.avg;
            return Math.abs(point.avg - rightAvg) / Math.max(point.avg, rightAvg, 1);
          });
        if (overlap.length === 0) {
          continue;
        }
        const score = overlap.reduce((total, value) => total + value, 0) / overlap.length;
        candidates.push({ left, right, score });
      }
    }
    return candidates.sort((a, b) => a.score - b.score).slice(0, 4).map(item => [item.left, item.right] as ComplexitySeries[]);
  }, [orderedSeries]);
  const customSeries = orderedSeries.filter(series => selectedChartLanguages.includes(series.language));

  React.useEffect(() => {
    setSelectedChartLanguages(current => {
      const available = orderedSeries.map(series => series.language);
      const filtered = current.filter(language => available.includes(language));
      return filtered.length > 0 ? filtered : available;
    });
  }, [orderedSeries]);
  if (!complexity || allPoints.length === 0) {
    return <section className="empty-chart">No benchmark points yet. Run a selected algorithm or launch runs from the API.</section>;
  }
  const languageStats = orderedSeries.map((series) => {
    const points = [...series.points].sort((left, right) => left.datasetSize - right.datasetSize);
    const avgOfAvg = points.reduce((total, point) => total + point.avg, 0) / Math.max(points.length, 1);
    const best = Math.min(...points.map(point => point.avg));
    const worst = Math.max(...points.map(point => point.avg));
    const avgP95 = points.reduce((total, point) => total + point.p95, 0) / Math.max(points.length, 1);
    const growth = points.length > 1 ? points[points.length - 1].avg / Math.max(points[0].avg, 1) : 1;
    return { language: series.language, avgOfAvg, best, worst, avgP95, growth, points: points.length };
  });

  return (
    <>
    <BenchmarkLineChart title="Interactive explorer: size vs orchestration wall time" series={orderedSeries} scale="linear" explorer />
    <BenchmarkLineChart title="Global comparison" series={orderedSeries} scale="linear" />
    <section className="chart-surface">
      <h2>Custom comparison</h2>
      <div className="chart-language-controls">
        <button type="button" className="sort-chip" onClick={() => setSelectedChartLanguages(orderedSeries.map(series => series.language))}>
          Select all
        </button>
        <button type="button" className="sort-chip" onClick={() => setSelectedChartLanguages([])}>
          Clear
        </button>
      </div>
      <div className="chart-language-controls">
        {orderedSeries.map(series => {
          const selected = selectedChartLanguages.includes(series.language);
          return (
            <button
              key={`toggle-${series.language}`}
              type="button"
              className="sort-chip"
              aria-pressed={selected}
              onClick={() =>
                setSelectedChartLanguages(current =>
                  current.includes(series.language) ? current.filter(item => item !== series.language) : [...current, series.language],
                )
              }
            >
              {series.language}
            </button>
          );
        })}
      </div>
      {customSeries.length === 0 ? (
        <p className="pair-note">Select at least one language to render the chart.</p>
      ) : (
        <BenchmarkLineChart title="Selected languages" series={customSeries} scale="linear" compact embedded />
      )}
    </section>
    <BenchmarkLineChart title="Global comparison (log scale)" series={orderedSeries} scale="log" />
    <section className="pair-grid">
      {closestPairs.map((pair, index) => (
        <PairChartCard key={pair.map(item => item.language).join('-')} pair={pair} title={`Closest matchup #${index + 1}`} />
      ))}
    </section>
    <section className="table-surface">
      <h2>Language stats</h2>
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              <th>Language</th>
              <th>Points</th>
              <th>Best avg ms</th>
              <th>Worst avg ms</th>
              <th>Mean avg ms</th>
              <th>Mean p95 ms</th>
              <th>Growth (last/first)</th>
            </tr>
          </thead>
          <tbody>
            {languageStats.map((stat) => (
              <tr key={stat.language}>
                <td>{stat.language}</td>
                <td>{stat.points}</td>
                <td>{Math.round(stat.best)}</td>
                <td>{Math.round(stat.worst)}</td>
                <td>{Math.round(stat.avgOfAvg)}</td>
                <td>{Math.round(stat.avgP95)}</td>
                <td>{stat.growth.toFixed(2)}x</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
    </>
  );
}

function BenchmarkLineChart({
  series,
  title,
  scale,
  compact = false,
  embedded = false,
  explorer = false,
}: {
  series: ComplexitySeries[];
  title: string;
  scale: ChartScale;
  compact?: boolean;
  embedded?: boolean;
  explorer?: boolean;
}) {
  const [zoomLevel, setZoomLevel] = React.useState(0);
  const [hoverPoint, setHoverPoint] = React.useState<ChartHoverPoint | null>(null);
  const clipId = `chart-clip-${React.useId().replace(/:/g, '')}`;
  const svgRef = React.useRef<SVGSVGElement | null>(null);
  const allPoints = series.flatMap(item => item.points);
  if (allPoints.length === 0) return null;

  const width = compact ? 680 : 980;
  const height = explorer ? 540 : compact ? 300 : 450;
  const padding = compact ? { top: 26, right: 48, bottom: 68, left: 86 } : { top: 34, right: 48, bottom: 72, left: 92 };
  const zoomFactors = [1, 1.5, 2.25, 3.5, 5];
  const zoomFactor = zoomFactors[zoomLevel];
  const minX = Math.min(...allPoints.map(point => point.datasetSize));
  const maxX = Math.max(...allPoints.map(point => point.datasetSize));
  const maxY = Math.max(...allPoints.map(point => point.avg), 1);
  const visibleMaxY = Math.max(maxY / zoomFactor, 1);
  const plotWidth = width - padding.left - padding.right;
  const plotHeight = height - padding.top - padding.bottom;
  const xScale = (value: number) => {
    if (minX === maxX) return padding.left + plotWidth / 2;
    return padding.left + ((value - minX) / (maxX - minX)) * plotWidth;
  };
  const yScale = (value: number) => {
    if (scale === 'log') {
      const logMax = Math.log10(Math.max(visibleMaxY, 1));
      return height - padding.bottom - (Math.log10(Math.max(value, 1)) / Math.max(logMax, 1)) * plotHeight;
    }
    return height - padding.bottom - (value / visibleMaxY) * plotHeight;
  };
  const ticks = Array.from(new Set(allPoints.map(point => point.datasetSize))).sort((a, b) => a - b);
  const yTicks =
    scale === 'log'
      ? [1, 10, 100, 1000, 10000, 100000].filter(tick => tick <= visibleMaxY)
      : linearTicks(visibleMaxY);
  const canZoomOut = zoomLevel > 0;
  const canZoomIn = zoomLevel < zoomFactors.length - 1;
  const tooltipWidth = 190;
  const tooltipHeight = 86;
  const tooltipX = hoverPoint ? Math.min(Math.max(hoverPoint.x + 14, padding.left), width - padding.right - tooltipWidth) : 0;
  const tooltipY = hoverPoint ? Math.min(Math.max(hoverPoint.y - tooltipHeight - 12, padding.top), height - padding.bottom - tooltipHeight) : 0;
  const activePointKey = hoverPoint ? `${hoverPoint.language}-${hoverPoint.point.datasetId}` : '';

  function updateHoverPoint(event: React.MouseEvent<SVGSVGElement>) {
    if (!explorer || !svgRef.current) {
      return;
    }
    const bounds = svgRef.current.getBoundingClientRect();
    const svgX = ((event.clientX - bounds.left) / bounds.width) * width;
    const svgY = ((event.clientY - bounds.top) / bounds.height) * height;
    if (svgX < padding.left || svgX > width - padding.right || svgY < padding.top || svgY > height - padding.bottom) {
      setHoverPoint(null);
      return;
    }
    let nearest: ChartHoverPoint | null = null;
    let nearestDistance = Number.POSITIVE_INFINITY;
    series.forEach(item => {
      const color = languageColors[item.language] ?? '#475569';
      item.points.forEach(point => {
        const pointX = xScale(point.datasetSize);
        const pointY = yScale(point.avg);
        if (pointY < padding.top || pointY > height - padding.bottom) {
          return;
        }
        const distance = Math.hypot(svgX - pointX, svgY - pointY);
        if (distance < nearestDistance) {
          nearestDistance = distance;
          nearest = { language: item.language, point, color, x: pointX, y: pointY };
        }
      });
    });
    setHoverPoint(nearest);
  }

  return (
    <section className={embedded ? 'chart-panel' : `chart-surface chart-surface-compact${explorer ? ' chart-explorer' : ''}`}>
      <div className="chart-heading">
        <div>
          <h2>{title}</h2>
          <p>
            {explorer
              ? `Hover a point for size - ms | Y zoom ${zoomFactor.toFixed(zoomFactor % 1 === 0 ? 0 : 1)}x`
              : scale === 'log'
                ? `Log scale | Y zoom ${zoomFactor.toFixed(zoomFactor % 1 === 0 ? 0 : 1)}x`
                : `Y zoom ${zoomFactor.toFixed(zoomFactor % 1 === 0 ? 0 : 1)}x`}
          </p>
        </div>
        <div className="chart-tools" role="group" aria-label={`${title} zoom controls`}>
          <button type="button" className="icon-button" onClick={() => setZoomLevel(level => Math.min(level + 1, zoomFactors.length - 1))} disabled={!canZoomIn} title="Zoom in">
            <ZoomIn size={16} />
          </button>
          <button type="button" className="icon-button" onClick={() => setZoomLevel(level => Math.max(level - 1, 0))} disabled={!canZoomOut} title="Zoom out">
            <ZoomOut size={16} />
          </button>
          <button type="button" className="icon-button" onClick={() => setZoomLevel(0)} disabled={!canZoomOut} title="Reset zoom">
            <RotateCcw size={16} />
          </button>
        </div>
      </div>
      <svg
        ref={svgRef}
        viewBox={`0 0 ${width} ${height}`}
        role="img"
        aria-label={`${title} chart`}
        className={explorer ? 'explorer-svg' : undefined}
        onMouseMove={updateHoverPoint}
        onMouseLeave={() => setHoverPoint(null)}
      >
        <defs>
          <clipPath id={clipId}>
            <rect x={padding.left} y={padding.top} width={plotWidth} height={plotHeight} />
          </clipPath>
        </defs>
        <line x1={padding.left} y1={padding.top} x2={padding.left} y2={height - padding.bottom} className="axis" />
        <line x1={padding.left} y1={height - padding.bottom} x2={width - padding.right} y2={height - padding.bottom} className="axis" />
        {yTicks.map((tick) => {
          const y = yScale(tick);
          return (
            <g key={`pair-y-${tick}`}>
              <line x1={padding.left} y1={y} x2={width - padding.right} y2={y} className="grid" />
              <text x={padding.left - 12} y={y + 4} textAnchor="end" className="tick">
                {formatDurationTick(tick)}
              </text>
            </g>
          );
        })}
        {ticks.map((tick, index) => {
          const isFirst = index === 0;
          const isLast = index === ticks.length - 1;
          return (
            <text
              key={`pair-${tick}`}
              x={xScale(tick)}
              y={height - 16}
              textAnchor={isFirst ? 'start' : isLast ? 'end' : 'middle'}
              className="tick"
            >
              {formatDatasetTick(tick)}
            </text>
          );
        })}
        <text x={padding.left + plotWidth / 2} y={height - 24} textAnchor="middle" className="axis-label">
          Input size
        </text>
        <text x={22} y={padding.top + plotHeight / 2} textAnchor="middle" className="axis-label" transform={`rotate(-90 22 ${padding.top + plotHeight / 2})`}>
          Orchestration wall time
        </text>
        <g clipPath={`url(#${clipId})`}>
          {series.map(item => {
            const points = [...item.points].sort((left, right) => left.datasetSize - right.datasetSize);
            const line = points.map(point => `${xScale(point.datasetSize)},${yScale(point.avg)}`).join(' ');
            const color = languageColors[item.language] ?? '#475569';
            return (
              <g key={`chart-line-${title}-${item.language}`}>
                <polyline points={line} fill="none" stroke={color} strokeWidth={explorer ? '5' : compact ? '3' : '4'} strokeLinejoin="round" strokeLinecap="round" />
                {points.map(point => (
                  <circle
                    key={`chart-point-${title}-${item.language}-${point.datasetId}`}
                    cx={xScale(point.datasetSize)}
                    cy={yScale(point.avg)}
                    r={activePointKey === `${item.language}-${point.datasetId}` ? (explorer ? '9' : '7') : explorer ? '6' : compact ? '4' : '5'}
                    fill={color}
                    className={explorer ? 'explorer-point' : undefined}
                  />
                ))}
              </g>
            );
          })}
        </g>
        {explorer && hoverPoint && (
          <g className="chart-hover-layer">
            <line x1={hoverPoint.x} y1={padding.top} x2={hoverPoint.x} y2={height - padding.bottom} className="crosshair" />
            <line x1={padding.left} y1={hoverPoint.y} x2={width - padding.right} y2={hoverPoint.y} className="crosshair" />
            <circle cx={hoverPoint.x} cy={hoverPoint.y} r="10" fill="none" stroke={hoverPoint.color} strokeWidth="3" />
            <g transform={`translate(${tooltipX} ${tooltipY})`}>
              <rect width={tooltipWidth} height={tooltipHeight} rx="8" className="chart-tooltip-box" />
              <circle cx="16" cy="18" r="5" fill={hoverPoint.color} />
              <text x="30" y="23" className="chart-tooltip-title">{hoverPoint.language}</text>
              <text x="14" y="48" className="chart-tooltip-text">Size: {formatDatasetTick(hoverPoint.point.datasetSize)}</text>
              <text x="14" y="70" className="chart-tooltip-text">ms: {formatDurationTick(hoverPoint.point.avg)}</text>
            </g>
          </g>
        )}
      </svg>
      <div className="legend">
        {series.map((item) => (
          <span key={`chart-legend-${title}-${item.language}`}>
            <i style={{ background: languageColors[item.language] ?? '#475569' }} />
            {item.language}
          </span>
        ))}
      </div>
    </section>
  );
}

function PairChartCard({ pair, title }: { pair: ComplexitySeries[]; title?: string }) {
  return <BenchmarkLineChart series={pair} title={title ?? (pair.length === 2 ? `${pair[0].language} vs ${pair[1].language}` : pair[0].language)} scale="linear" compact />;
}

function RunTable({ runs }: { runs: RunSummary[] }) {
  const [sortBy, setSortBy] = React.useState<
    'languageSize' | 'id' | 'status' | 'algorithmName' | 'language' | 'datasetSize' | 'orchestrationWallTimeMs'
  >('languageSize');
  const [sortDirection, setSortDirection] = React.useState<'asc' | 'desc'>('asc');

  const sortedRuns = React.useMemo(() => {
    const direction = sortDirection === 'asc' ? 1 : -1;
    const sorted = [...runs];
    sorted.sort((left, right) => {
      const numberCompare = (a: number | null | undefined, b: number | null | undefined) => (a ?? -1) - (b ?? -1);

      if (sortBy === 'languageSize') {
        const byLanguage = left.language.localeCompare(right.language) * direction;
        if (byLanguage !== 0) return byLanguage;
        const bySize = numberCompare(left.datasetSize, right.datasetSize) * direction;
        if (bySize !== 0) return bySize;
        return numberCompare(right.id, left.id);
      }
      if (sortBy === 'id') return numberCompare(left.id, right.id) * direction;
      if (sortBy === 'datasetSize') return numberCompare(left.datasetSize, right.datasetSize) * direction;
      if (sortBy === 'orchestrationWallTimeMs') {
        return numberCompare(left.orchestrationWallTimeMs, right.orchestrationWallTimeMs) * direction;
      }
      if (sortBy === 'status') return left.status.localeCompare(right.status) * direction;
      if (sortBy === 'algorithmName') return left.algorithmName.localeCompare(right.algorithmName) * direction;
      return left.language.localeCompare(right.language) * direction;
    });
    return sorted;
  }, [runs, sortBy, sortDirection]);

  function updateSort(
    nextSortBy: 'languageSize' | 'id' | 'status' | 'algorithmName' | 'language' | 'datasetSize' | 'orchestrationWallTimeMs',
  ) {
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
        <button type="button" className="sort-chip" onClick={() => updateSort('languageSize')} aria-pressed={sortBy === 'languageSize'}>
          Language + Size {sortBy === 'languageSize' ? (sortDirection === 'asc' ? '^' : 'v') : ''}
        </button>
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
        <button
          type="button"
          className="sort-chip"
          onClick={() => updateSort('orchestrationWallTimeMs')}
          aria-pressed={sortBy === 'orchestrationWallTimeMs'}
        >
          Orchestration ms {sortBy === 'orchestrationWallTimeMs' ? (sortDirection === 'asc' ? '↑' : '↓') : ''}
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
              <th>Orchestration ms</th>
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
                <td>{run.orchestrationWallTimeMs ?? '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

createRoot(document.getElementById('root')!).render(<App />);
