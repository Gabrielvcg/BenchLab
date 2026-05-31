package com.vacaro.benchlab.web.api;

import com.vacaro.benchlab.service.BenchmarkService;
import com.vacaro.benchlab.service.api.dto.BenchmarkCompareResponse;
import com.vacaro.benchlab.service.api.dto.BenchmarkCompareRow;
import com.vacaro.benchlab.service.api.dto.BenchmarkComplexityPoint;
import com.vacaro.benchlab.service.api.dto.BenchmarkComplexityResponse;
import com.vacaro.benchlab.service.api.dto.BenchmarkComplexitySeries;
import com.vacaro.benchlab.service.api.dto.BenchmarkTimeseriesPoint;
import com.vacaro.benchlab.service.api.dto.BenchmarkTimeseriesResponse;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class BenchmarksApiDelegateHandler implements BenchmarksApiDelegate {

    private final BenchmarkService benchmarkService;

    public BenchmarksApiDelegateHandler(BenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    @Override
    public ResponseEntity<BenchmarkCompareResponse> compareBenchmarks(Long algorithmId, Long datasetId) {
        var response = benchmarkService.compare(algorithmId, datasetId);
        var apiResponse = new BenchmarkCompareResponse().algorithmId(response.algorithmId()).datasetId(response.datasetId());
        apiResponse.setRows(
            response
                .rows()
                .stream()
                .map(row ->
                    new BenchmarkCompareRow()
                        .language(row.language())
                        .avg(row.avg())
                        .stddev(row.stddev())
                        .p50(row.p50())
                        .p95(row.p95())
                        .validSamples(row.validSamples())
                )
                .collect(Collectors.toList())
        );
        return ResponseEntity.ok(apiResponse);
    }

    @Override
    public ResponseEntity<BenchmarkTimeseriesResponse> benchmarkTimeseries(Long algorithmId, String language) {
        var response = benchmarkService.timeseries(algorithmId, language);
        var apiResponse = new BenchmarkTimeseriesResponse().algorithmId(response.algorithmId()).language(response.language());
        apiResponse.setPoints(
            response
                .points()
                .stream()
                .map(point -> new BenchmarkTimeseriesPoint().finishedAt(point.finishedAt()).wallTimeMs(point.wallTimeMs()))
                .collect(Collectors.toList())
        );
        return ResponseEntity.ok(apiResponse);
    }

    @Override
    public ResponseEntity<BenchmarkComplexityResponse> benchmarkComplexity(Long algorithmId, String metric) {
        var response = benchmarkService.complexity(algorithmId, metric);
        var apiResponse = new BenchmarkComplexityResponse().algorithmId(response.algorithmId()).metric(response.metric());
        apiResponse.setSeries(
            response
                .series()
                .stream()
                .map(series -> {
                    var apiSeries = new BenchmarkComplexitySeries().language(series.language());
                    apiSeries.setPoints(
                        series
                            .points()
                            .stream()
                            .map(point ->
                                new BenchmarkComplexityPoint()
                                    .datasetId(point.datasetId())
                                    .datasetSize(point.datasetSize())
                                    .avg(point.avg())
                                    .stddev(point.stddev())
                                    .p50(point.p50())
                                    .p95(point.p95())
                                    .validSamples(point.validSamples())
                            )
                            .collect(Collectors.toList())
                    );
                    return apiSeries;
                })
                .collect(Collectors.toList())
        );
        return ResponseEntity.ok(apiResponse);
    }
}
