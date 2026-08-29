package com.vacaro.benchlab;

import com.vacaro.benchlab.config.AsyncSyncConfiguration;
import com.vacaro.benchlab.config.EmbeddedRedis;
import com.vacaro.benchlab.config.EmbeddedSQL;
import com.vacaro.benchlab.config.JacksonConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Base composite annotation for integration tests.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(classes = { BenchLabApp.class, JacksonConfiguration.class, AsyncSyncConfiguration.class })
@EmbeddedRedis
@EmbeddedSQL
public @interface IntegrationTest {
}
