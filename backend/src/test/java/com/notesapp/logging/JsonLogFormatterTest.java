package com.notesapp.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonLogFormatterTest {
    private final JsonLogFormatter formatter = new JsonLogFormatter(new MockEnvironment());
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void errorHasMessageAndStackInSingleJsonLine() throws Exception {
        var event = event(Level.ERROR);
        event.setThrowableProxy(new ThrowableProxy(new IllegalStateException("failed",
                new IllegalArgumentException("root cause"))));
        String output = formatter.format(event);
        var record = json.readTree(output);
        assertThat(record.at("/error/message").asText()).isEqualTo("failed");
        assertThat(record.at("/error/stack").asText()).contains("IllegalStateException", "root cause");
        assertThat(record.path("severity_number").asInt()).isEqualTo(17);
        assertThat(output.lines().count()).isEqualTo(1);
    }

    @Test
    void startupHasMetadataWithoutInventedRequestOrDuration() throws Exception {
        var record = json.readTree(formatter.format(event(Level.INFO)));
        assertThat(record.path("timestamp").asText()).isEqualTo("2023-11-14T22:13:20.123Z");
        assertThat(record.path("message").asText()).isEqualTo("Service started");
        assertThat(record.at("/service/name").asText()).isEqualTo("notes-backend");
        assertThat(record.at("/service/version").asText()).isEqualTo("dev");
        assertThat(record.at("/deployment/environment/name").asText()).isEqualTo("local");
        assertThat(record.at("/host/name").asText()).isNotBlank();
        for (String field : new String[]{"request_id", "trace_id", "duration_ms", "error", "k8s"}) {
            assertThat(record.has(field)).as(field).isFalse();
        }
    }

    @Test
    void severityNumbersFollowOpenTelemetryRanges() throws Exception {
        Map<Level, Integer> levels = Map.of(Level.TRACE, 1, Level.DEBUG, 5, Level.INFO, 9,
                Level.WARN, 13, Level.ERROR, 17);
        for (var entry : levels.entrySet()) {
            var record = json.readTree(formatter.format(event(entry.getKey())));
            assertThat(record.path("level").asText()).isEqualTo(entry.getKey().toString());
            assertThat(record.path("severity_number").asInt()).isEqualTo(entry.getValue());
        }
    }

    private LoggingEvent event(Level level) {
        var event = new LoggingEvent();
        event.setLevel(level);
        event.setMessage("Service started");
        event.setLoggerName("test");
        event.setThreadName("main");
        event.setTimeStamp(1700000000123L);
        event.setMDCPropertyMap(Map.of());
        return event;
    }
}
