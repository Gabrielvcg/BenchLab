package com.vacaro.benchlab.web.rest.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vacaro.benchlab.IntegrationTest;
import com.vacaro.benchlab.domain.BenchmarkRun;
import com.vacaro.benchlab.domain.BenchmarkRunStatus;
import com.vacaro.benchlab.domain.User;
import com.vacaro.benchlab.repository.BenchmarkRunRepository;
import com.vacaro.benchlab.repository.UserRepository;
import com.vacaro.benchlab.service.messaging.RunEventPublisher;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@IntegrationTest
class BenchmarkResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BenchmarkRunRepository benchmarkRunRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private RunEventPublisher runEventPublisher;

    @Test
    @Transactional
    @WithMockUser("benchmark-user")
    void shouldCreateAlgorithmDatasetImplementationAndRun() throws Exception {
        persistBenchmarkUser();
        MvcResult algorithmResult = mockMvc
            .perform(
                post("/api/algorithms")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            Map.of("name", "quick-sort", "category", "sorting", "version", "v1", "complexityDeclared", "O(n log n)")
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNumber())
            .andReturn();

        Long algorithmId = objectMapper.readTree(algorithmResult.getResponse().getContentAsByteArray()).get("id").asLong();

        MvcResult datasetResult = mockMvc
            .perform(
                post("/api/datasets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            Map.of("type", "random-int-array", "sizeValue", 1000, "seed", 42, "checksum", "abc123", "datasetVersion", "v1")
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNumber())
            .andReturn();

        Long datasetId = objectMapper.readTree(datasetResult.getResponse().getContentAsByteArray()).get("id").asLong();

        MvcResult implementationResult = mockMvc
            .perform(
                post("/api/implementations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            Map.of(
                                "algorithmId",
                                algorithmId,
                                "language",
                                "PYTHON",
                                "sourceCode",
                                "print('hello benchmark')",
                                "compileConfig",
                                "",
                                "runtimeConfig",
                                ""
                            )
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNumber())
            .andReturn();

        Long implementationId = objectMapper.readTree(implementationResult.getResponse().getContentAsByteArray()).get("id").asLong();

        MvcResult runResult = mockMvc
            .perform(
                post("/api/runs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            Map.of(
                                "implementationId",
                                implementationId,
                                "datasetId",
                                datasetId,
                                "timeoutMs",
                                5000,
                                "memoryMb",
                                128,
                                "cpuLimit",
                                1.0,
                                "iterations",
                                3
                            )
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.status").value("QUEUED"))
            .andReturn();

        Long runId = objectMapper.readTree(runResult.getResponse().getContentAsByteArray()).get("id").asLong();

        mockMvc.perform(get("/api/runs/{id}", runId)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(runId));
    }

    @Test
    @Transactional
    @WithMockUser("benchmark-user")
    void shouldAcceptInternalWorkerCallbackWithToken() throws Exception {
        persistBenchmarkUser();
        MvcResult algorithmResult = mockMvc
            .perform(
                post("/api/algorithms")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            Map.of("name", "binary-search", "category", "search", "version", "v1", "complexityDeclared", "O(log n)")
                        )
                    )
            )
            .andExpect(status().isOk())
            .andReturn();

        Long algorithmId = objectMapper.readTree(algorithmResult.getResponse().getContentAsByteArray()).get("id").asLong();

        MvcResult datasetResult = mockMvc
            .perform(
                post("/api/datasets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            Map.of("type", "sorted-array", "sizeValue", 100, "seed", 7, "checksum", "xyz", "datasetVersion", "v1")
                        )
                    )
            )
            .andExpect(status().isOk())
            .andReturn();

        Long datasetId = objectMapper.readTree(datasetResult.getResponse().getContentAsByteArray()).get("id").asLong();

        MvcResult implementationResult = mockMvc
            .perform(
                post("/api/implementations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            Map.of(
                                "algorithmId",
                                algorithmId,
                                "language",
                                "PYTHON",
                                "sourceCode",
                                "print('done')",
                                "compileConfig",
                                "",
                                "runtimeConfig",
                                ""
                            )
                        )
                    )
            )
            .andExpect(status().isOk())
            .andReturn();

        Long implementationId = objectMapper.readTree(implementationResult.getResponse().getContentAsByteArray()).get("id").asLong();

        MvcResult runResult = mockMvc
            .perform(
                post("/api/runs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            Map.of("implementationId", implementationId, "datasetId", datasetId, "iterations", 3)
                        )
                    )
            )
            .andExpect(status().isOk())
            .andReturn();

        Long runId = objectMapper.readTree(runResult.getResponse().getContentAsByteArray()).get("id").asLong();

        mockMvc
            .perform(
                post("/api/internal/runs/{id}/start", runId)
                    .header("X-Worker-Token", "benchlab-internal-token")
                    .header("X-Runner-Host", "worker-1")
            )
            .andExpect(status().isNoContent());

        BenchmarkRun startedRun = benchmarkRunRepository.findById(runId).orElseThrow();
        assertThat(startedRun.getStatus()).isEqualTo(BenchmarkRunStatus.RUNNING);
        assertThat(startedRun.getStartedAt()).isNotNull();

        mockMvc
            .perform(
                post("/api/internal/runs/{id}/result", runId)
                    .header("X-Worker-Token", "benchlab-internal-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            Map.ofEntries(
                                Map.entry("status", "SUCCEEDED"),
                                Map.entry("runnerHost", "worker-1"),
                                Map.entry("orchestrationWallTimeMs", 12),
                                Map.entry("exitCode", 0),
                                Map.entry("timedOut", false),
                                Map.entry("compileWallTimeMs", 0),
                                Map.entry("stdoutPreview", "ok"),
                                Map.entry("stderrPreview", ""),
                                Map.entry("stdoutTruncated", false),
                                Map.entry("stderrTruncated", false),
                                Map.entry("outputSizeBytes", 2),
                                Map.entry("artifactChecksum", "aaa"),
                                Map.entry("technicalLogSummary", "ok")
                            )
                        )
                    )
            )
            .andExpect(status().isNoContent());

        BenchmarkRun run = benchmarkRunRepository.findById(runId).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(BenchmarkRunStatus.SUCCEEDED);

        mockMvc.perform(get("/api/algorithms")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(algorithmId));

        mockMvc.perform(get("/api/datasets")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(datasetId));

        mockMvc
            .perform(get("/api/runs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(runId))
            .andExpect(jsonPath("$[0].orchestrationWallTimeMs").value(12));

        mockMvc
            .perform(
                get("/api/benchmarks/complexity")
                    .param("algorithmId", algorithmId.toString())
                    .param("metric", "orchestrationWallTimeMs")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.series[0].language").value("PYTHON"))
            .andExpect(jsonPath("$.series[0].points[0].datasetSize").value(100))
            .andExpect(jsonPath("$.series[0].points[0].avg").value(12.0));
    }

    @Test
    @Transactional
    @WithMockUser("benchmark-user")
    void shouldRejectInternalWorkerCallbackWithoutToken() throws Exception {
        persistBenchmarkUser();
        MvcResult algorithmResult = mockMvc
            .perform(
                post("/api/algorithms")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            Map.of("name", "heap-sort", "category", "sorting", "version", "v1", "complexityDeclared", "O(n log n)")
                        )
                    )
            )
            .andExpect(status().isOk())
            .andReturn();
        Long algorithmId = objectMapper.readTree(algorithmResult.getResponse().getContentAsByteArray()).get("id").asLong();

        MvcResult datasetResult = mockMvc
            .perform(
                post("/api/datasets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            Map.of("type", "random", "sizeValue", 10, "seed", 1, "checksum", "1", "datasetVersion", "v1")
                        )
                    )
            )
            .andExpect(status().isOk())
            .andReturn();
        Long datasetId = objectMapper.readTree(datasetResult.getResponse().getContentAsByteArray()).get("id").asLong();

        MvcResult implementationResult = mockMvc
            .perform(
                post("/api/implementations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            Map.of(
                                "algorithmId",
                                algorithmId,
                                "language",
                                "PYTHON",
                                "sourceCode",
                                "print('x')",
                                "compileConfig",
                                "",
                                "runtimeConfig",
                                ""
                            )
                        )
                    )
            )
            .andExpect(status().isOk())
            .andReturn();
        JsonNode implementationNode = objectMapper.readTree(implementationResult.getResponse().getContentAsByteArray());

        MvcResult runResult = mockMvc
            .perform(
                post("/api/runs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            Map.of("implementationId", implementationNode.get("id").asLong(), "datasetId", datasetId)
                        )
                    )
            )
            .andExpect(status().isOk())
            .andReturn();

        Long runId = objectMapper.readTree(runResult.getResponse().getContentAsByteArray()).get("id").asLong();

        mockMvc
            .perform(
                post("/api/internal/runs/{id}/result", runId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            Map.of(
                                "status",
                                "SUCCEEDED",
                                "runnerHost",
                                "worker-1",
                                "orchestrationWallTimeMs",
                                1,
                                "exitCode",
                                0,
                                "timedOut",
                                false,
                                "compileWallTimeMs",
                                0,
                                "stdoutTruncated",
                                false,
                                "stderrTruncated",
                                false,
                                "outputSizeBytes",
                                0
                            )
                        )
                    )
            )
            .andExpect(status().isUnauthorized());
    }

    private void persistBenchmarkUser() {
        User user = new User();
        user.setLogin("benchmark-user");
        user.setEmail("benchmark-user@example.com");
        user.setActivated(true);
        user.setPassword(passwordEncoder.encode("test-password"));
        userRepository.saveAndFlush(user);
    }
}
