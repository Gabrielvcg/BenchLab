package com.vacaro.benchlab.web.api;

import com.vacaro.benchlab.service.BenchmarkService;
import com.vacaro.benchlab.service.api.dto.CreateDatasetRequest;
import com.vacaro.benchlab.service.api.dto.DatasetResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DatasetsApiDelegateHandler implements DatasetsApiDelegate {

    private final BenchmarkService benchmarkService;

    public DatasetsApiDelegateHandler(BenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    @Override
    public ResponseEntity<DatasetResponse> createDataset(CreateDatasetRequest createDatasetRequest) {
        var saved = benchmarkService.createDataset(
            new com.vacaro.benchlab.service.dto.benchmark.CreateDatasetRequest(
                createDatasetRequest.getType(),
                createDatasetRequest.getSizeValue(),
                createDatasetRequest.getSeed(),
                createDatasetRequest.getChecksum(),
                createDatasetRequest.getDatasetVersion()
            )
        );

        return ResponseEntity.ok(toDatasetResponse(saved));
    }

    @Override
    public ResponseEntity<List<DatasetResponse>> listDatasets() {
        return ResponseEntity.ok(benchmarkService.listDatasets().stream().map(DatasetsApiDelegateHandler::toDatasetResponse).toList());
    }

    private static DatasetResponse toDatasetResponse(com.vacaro.benchlab.service.dto.benchmark.DatasetResponse dataset) {
        return new DatasetResponse()
            .id(dataset.id())
            .type(dataset.type())
            .sizeValue(dataset.sizeValue())
            .seed(dataset.seed())
            .checksum(dataset.checksum())
            .datasetVersion(dataset.datasetVersion());
    }
}
