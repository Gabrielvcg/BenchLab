package com.vacaro.benchlab.web.rest.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.vacaro.benchlab.config.BenchLabProperties;
import com.vacaro.benchlab.service.BenchmarkService;
import com.vacaro.benchlab.web.api.AlgorithmsApiController;
import com.vacaro.benchlab.web.api.AlgorithmsApiDelegateHandler;
import com.vacaro.benchlab.web.api.BenchmarksApiController;
import com.vacaro.benchlab.web.api.BenchmarksApiDelegateHandler;
import com.vacaro.benchlab.web.api.DatasetsApiController;
import com.vacaro.benchlab.web.api.DatasetsApiDelegateHandler;
import com.vacaro.benchlab.web.api.ImplementationsApiController;
import com.vacaro.benchlab.web.api.ImplementationsApiDelegateHandler;
import com.vacaro.benchlab.web.api.RunsApiController;
import com.vacaro.benchlab.web.api.RunsApiDelegateHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@WebMvcTest(
    controllers = {
        AlgorithmsApiController.class,
        BenchmarksApiController.class,
        DatasetsApiController.class,
        ImplementationsApiController.class,
        RunsApiController.class,
        InternalRunCallbackResource.class,
    }
)
@Import(
    {
        AlgorithmsApiDelegateHandler.class,
        BenchmarksApiDelegateHandler.class,
        DatasetsApiDelegateHandler.class,
        ImplementationsApiDelegateHandler.class,
        RunsApiDelegateHandler.class,
    }
)
class BenchmarkRouteOwnershipTest {

    private static final List<Route> PUBLIC_ROUTES = List.of(
        new Route("/api/algorithms", RequestMethod.GET),
        new Route("/api/algorithms", RequestMethod.POST),
        new Route("/api/datasets", RequestMethod.GET),
        new Route("/api/datasets", RequestMethod.POST),
        new Route("/api/implementations", RequestMethod.POST),
        new Route("/api/runs", RequestMethod.GET),
        new Route("/api/runs", RequestMethod.POST),
        new Route("/api/runs/{id}", RequestMethod.GET),
        new Route("/api/benchmarks/compare", RequestMethod.GET),
        new Route("/api/benchmarks/timeseries", RequestMethod.GET),
        new Route("/api/benchmarks/complexity", RequestMethod.GET)
    );

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @MockBean
    private BenchmarkService benchmarkService;

    @MockBean
    private BenchLabProperties benchLabProperties;

    @Test
    void eachPublicBenchmarkRouteHasOneOwner() {
        PUBLIC_ROUTES.forEach(route -> {
            long owners = handlerMapping
                .getHandlerMethods()
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().getPatternValues().contains(route.path()))
                .filter(entry -> entry.getKey().getMethodsCondition().getMethods().contains(route.method()))
                .count();

            assertThat(owners).as("owners for %s %s", route.method(), route.path()).isEqualTo(1);
        });
    }

    private record Route(String path, RequestMethod method) {}
}
