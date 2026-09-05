package com.vacaro.benchlab.service;

import com.vacaro.benchlab.service.dto.benchmark.AlgorithmResponse;
import com.vacaro.benchlab.service.dto.benchmark.BenchmarkCompareResponse;
import com.vacaro.benchlab.service.dto.benchmark.BenchmarkComplexityResponse;
import com.vacaro.benchlab.service.dto.benchmark.BenchmarkTimeseriesResponse;
import com.vacaro.benchlab.service.dto.benchmark.CreateAlgorithmRequest;
import com.vacaro.benchlab.service.dto.benchmark.CreateDatasetRequest;
import com.vacaro.benchlab.service.dto.benchmark.CreateImplementationRequest;
import com.vacaro.benchlab.service.dto.benchmark.CreateRunRequest;
import com.vacaro.benchlab.service.dto.benchmark.DatasetResponse;
import com.vacaro.benchlab.service.dto.benchmark.ImplementationResponse;
import com.vacaro.benchlab.service.dto.benchmark.RunResponse;
import com.vacaro.benchlab.service.dto.benchmark.RunResultCallbackRequest;
import com.vacaro.benchlab.service.dto.benchmark.RunSummaryResponse;
import java.util.List;

public interface BenchmarkService {
    AlgorithmResponse createAlgorithm(CreateAlgorithmRequest request);
    DatasetResponse createDataset(CreateDatasetRequest request);
    ImplementationResponse createImplementation(CreateImplementationRequest request);
    RunResponse createRun(CreateRunRequest request);
    RunResponse getRun(Long runId);
    List<AlgorithmResponse> listAlgorithms();
    List<DatasetResponse> listDatasets();
    List<RunSummaryResponse> listRecentRuns();

    List<RunSummaryResponse> listRunHistory(Long beforeId);
    void markRunStarted(Long runId, String runnerHost);
    void registerRunResult(Long runId, RunResultCallbackRequest request);
    BenchmarkCompareResponse compare(Long algorithmId, Long datasetId);
    BenchmarkTimeseriesResponse timeseries(Long algorithmId, String language);
    BenchmarkComplexityResponse complexity(Long algorithmId, String metric);
}
