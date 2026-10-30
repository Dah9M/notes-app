package com.notesapp.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.logging.structured.StructuredLogFormatter;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared schema for application and framework logs, including startup events. */
public final class JsonLogFormatter implements StructuredLogFormatter<ILoggingEvent> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter TIMESTAMP = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
    private final Map<String, Object> metadata = new LinkedHashMap<>();

    public JsonLogFormatter(Environment environment) {
        put(metadata, "service.name", environment.getProperty("spring.application.name", "notes-backend"));
        put(metadata, "service.version", environment.getProperty("app.version", "dev"));
        put(metadata, "deployment.environment.name", environment.getProperty("app.environment", "local"));
        put(metadata, "host.name", environment.getProperty("HOSTNAME", hostname()));
        String pod = environment.getProperty("K8S_POD_NAME");
        if (pod != null && !pod.isBlank()) {
            put(metadata, "k8s.pod.name", pod);
        }
    }

    @Override
    public String format(ILoggingEvent event) {
        Map<String, Object> record = new LinkedHashMap<>();
        event.getMDCPropertyMap().forEach((key, value) -> put(record, key, value));
        if (event.getKeyValuePairs() != null) {
            event.getKeyValuePairs().forEach(pair -> put(record, pair.key, pair.value));
        }
        record.putAll(metadata);
        record.put("timestamp", TIMESTAMP.format(event.getInstant()));
        record.put("level", event.getLevel().toString());
        record.put("severity_number", switch (event.getLevel().toString()) {
            case "TRACE" -> 1;
            case "DEBUG" -> 5;
            case "WARN" -> 13;
            case "ERROR" -> 17;
            default -> 9;
        });
        record.put("message", event.getFormattedMessage());
        record.put("logger", event.getLoggerName());
        record.put("thread", event.getThreadName());
        if (event.getThrowableProxy() != null) {
            var error = event.getThrowableProxy();
            put(record, "error.type", error.getClassName());
            put(record, "error.message", error.getMessage());
            put(record, "error.stack", ThrowableProxyUtil.asString(error));
        }
        try {
            return JSON.writeValueAsString(record) + "\n";
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize log event", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static void put(Map<String, Object> record, String key, Object value) {
        String[] parts = key.split("\\.");
        Map<String, Object> target = record;
        for (int i = 0; i < parts.length - 1; i++) {
            Object existing = target.get(parts[i]);
            if (!(existing instanceof Map)) {
                existing = new LinkedHashMap<String, Object>();
                target.put(parts[i], existing);
            }
            target = (Map<String, Object>) existing;
        }
        target.put(parts[parts.length - 1], value);
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            return "unknown";
        }
    }
}
