import React from 'react';
import Plotly from 'plotly.js-basic-dist-min';

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

const languageColors: Record<string, string> = {
  PYTHON: '#1f77b4',
  JAVA: '#d97706',
  C: '#7c3aed',
  GO: '#0891b2',
  RUBY: '#dc2626',
  RUST: '#92400e',
  ASSEMBLY: '#475569',
};

function formatDurationTick(value: number): string {
  if (value >= 1000) {
    return `${(value / 1000).toLocaleString('es-ES', { maximumFractionDigits: 1 })}s`;
  }
  return `${Math.round(value).toLocaleString('es-ES')}ms`;
}

export default function BenchmarkLineChart({ series, title }: { series: ComplexitySeries[]; title: string }) {
  const chartRef = React.useRef<HTMLDivElement | null>(null);
  const chartData = React.useMemo<Plotly.Data[]>(
    () =>
      series.map(item => ({
        type: 'scatter',
        mode: 'lines+markers',
        name: item.language,
        x: item.points.map(point => point.datasetSize),
        y: item.points.map(point => point.p50),
        connectgaps: true,
        line: { color: languageColors[item.language] ?? '#475569', width: 3 },
        marker: { color: languageColors[item.language] ?? '#475569', size: 8, line: { color: '#fbfdfc', width: 2 } },
        error_y: {
          type: 'data',
          array: item.points.map(point => point.stddev),
          visible: true,
          thickness: 1.5,
          width: 4,
        },
        customdata: item.points.map(point => [point.avg, point.p95, point.validSamples]),
        hovertemplate: `<b>${item.language}</b><br>Input size: %{x:,}<br>Median: %{y:.2f} ms<br>Average: %{customdata[0]:.2f} ms<br>P95: %{customdata[1]:.2f} ms<br>Samples: %{customdata[2]}<extra></extra>`,
      })),
    [series],
  );

  React.useEffect(() => {
    const chart = chartRef.current;
    if (!chart || chartData.length === 0) return undefined;

    const layout: Partial<Plotly.Layout> = {
      autosize: true,
      height: 500,
      margin: { t: 18, r: 24, b: 74, l: 84 },
      paper_bgcolor: 'transparent',
      plot_bgcolor: '#fbfdfc',
      hovermode: 'closest',
      dragmode: 'pan',
      hoverdistance: 32,
      uirevision: 'benchlab-comparison',
      font: { family: 'IBM Plex Sans, system-ui, sans-serif', color: '#40524a' },
      xaxis: {
        title: { text: 'Input size', standoff: 18 },
        tickformat: ',d',
        gridcolor: '#dce4df',
        zeroline: false,
        fixedrange: false,
      },
      yaxis: {
        title: { text: 'Wall time (ms)', standoff: 12 },
        tickformat: '~s',
        gridcolor: '#dce4df',
        zeroline: false,
        fixedrange: false,
      },
      legend: { orientation: 'h', y: -0.22, x: 0, itemclick: 'toggle', itemdoubleclick: 'toggleothers' },
    };
    const config: Partial<Plotly.Config> = {
      responsive: true,
      scrollZoom: true,
      displayModeBar: true,
      displaylogo: false,
      doubleClick: 'reset',
      modeBarButtonsToRemove: ['select2d', 'lasso2d', 'autoScale2d'],
      toImageButtonOptions: { format: 'svg', filename: 'benchlab-comparison' },
    };

    void Plotly.react(chart, chartData, layout, config);
    const resizeObserver = new ResizeObserver(() => {
      void Plotly.Plots.resize(chart);
    });
    resizeObserver.observe(chart);

    return () => {
      resizeObserver.disconnect();
      Plotly.purge(chart);
    };
  }, [chartData]);

  return (
    <div className="interactive-chart">
      <div className="chart-heading">
        <div>
          <h2>{title}</h2>
          <p>Mediana del proceso dentro del contenedor · barras: desviación estándar · arrastra para moverte · rueda para ampliar</p>
        </div>
        <span className="chart-mode-badge">Pan mode</span>
      </div>
      <div ref={chartRef} className="plotly-chart" aria-label={`${title} chart`} />
    </div>
  );
}
