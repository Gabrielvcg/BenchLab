package com.vacaro.benchlab.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

class RedisTestContainersSpringContextCustomizerFactoryTest {

    @Test
    void usesDockerHostAndPublishedPortForHostJvmConnections() {
        GenericContainer<?> container = mock(GenericContainer.class);
        when(container.getHost()).thenReturn("127.0.0.1");
        when(container.getMappedPort(6379)).thenReturn(32789);

        assertThat(RedisTestContainersSpringContextCustomizerFactory.redisServerUrl(container)).isEqualTo("redis://127.0.0.1:32789");
    }
}
