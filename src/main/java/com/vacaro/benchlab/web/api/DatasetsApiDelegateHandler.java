package com.vacaro.benchlab.web.api;

import com.vacaro.benchlab.service.BenchmarkService;
import com.vacaro.benchlab.service.api.dto.CreateDatasetRequest;
import com.vacaro.benchlab.service.api.dto.DatasetResponse;
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

        return ResponseEntity.ok(
            new DatasetResponse()
                .id(saved.id())
                .type(saved.type())
                .sizeValue(saved.sizeValue())
                .seed(saved.seed())
                .checksum(saved.checksum())
                .datasetVersion(saved.datasetVersion())
        );
    }
}
