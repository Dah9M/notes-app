package com.notesapp.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessMetricsTest {

    @Test
    void allFiveCountersExistAtZeroBeforeAnyBusinessOperation() {
        var registry = new SimpleMeterRegistry();
        try {
            new BusinessMetrics(registry);
            assertThat(registry.getMeters()).hasSize(5);
            registry.getMeters().forEach(meter -> {
                assertThat(registry.get(meter.getId().getName()).counter().count()).isZero();
                assertThat(meter.getId().getTags()).isEmpty();
            });
        } finally {
            registry.close();
        }
    }
}
