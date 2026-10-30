package com.notesapp.auth;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestLoggingFilterTest {
    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>() {
        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };
    private Level previousLevel;

    @BeforeEach
    void setUp() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.TRACE);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
        appender.stop();
        MDC.clear();
    }

    @Test
    void completedRequest_hasCorrelationRouteStatusDurationAndAuthenticatedUser() throws Exception {
        var request = new MockHttpServletRequest("PUT", "/api/notes/123");
        request.setQueryString("secret=private");
        request.addHeader("X-Request-Id", "untrusted");
        var response = new MockHttpServletResponse();
        MDC.put("existing", "preserved");

        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("request_id")).isEqualTo(response.getHeader("X-Request-Id"));
            assertThat(MDC.get("request_id")).isNotEqualTo("untrusted");
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/notes/{id}");
            request.setAttribute(AuthInterceptor.USER_ID_ATTRIBUTE, 7L);
            // The interceptor clears userId before the filter writes the completion event.
            MDC.remove("userId");
            response.setStatus(204);
        });

        var event = lastEvent();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(fields(event)).containsEntry("http.route", "/api/notes/{id}")
                .containsEntry("http.response.status_code", 204)
                .containsEntry("event.outcome", "success");
        assertThat((Double) fields(event).get("duration_ms")).isGreaterThanOrEqualTo(0.0);
        assertThat(event.getMDCPropertyMap()).containsEntry("userId", "7")
                .containsEntry("http.request.method", "PUT")
                .containsEntry("request_id", response.getHeader("X-Request-Id"));
        assertThat(fields(event).toString()).doesNotContain("secret", "private", "/123");
        assertThat(MDC.getCopyOfContextMap()).containsExactlyEntriesOf(Map.of("existing", "preserved"));
    }

    @Test
    void unhandledFailure_isLoggedWithCauseAndRethrownAndContextIsCleared() {
        var exception = new ServletException("failure");
        var response = new MockHttpServletResponse();
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("GET", "/api/notes"),
                response, (req, res) -> { throw exception; })).isSameAs(exception);

        assertThat(appender.list).anySatisfy(event -> {
            assertThat(fields(event)).containsEntry("event.action", "http.request.failed");
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy()).isNotNull();
        });
        assertThat(fields(lastEvent())).containsEntry("http.response.status_code", 500)
                .containsEntry("event.outcome", "failure");
        assertThat(MDC.get("request_id")).isNull();
        assertThat(MDC.get("http.request.method")).isNull();
    }

    @Test
    void handledServerError_isErrorLevel() throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/notes"), response,
                (req, res) -> response.setStatus(503));
        assertThat(lastEvent().getLevel()).isEqualTo(Level.ERROR);
        assertThat(fields(lastEvent())).containsEntry("http.response.status_code", 503);
    }

    @Test
    void healthChecks_areDebugButFailuresAreError() throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health/readiness"), response,
                (req, res) -> {});
        assertThat(lastEvent().getLevel()).isEqualTo(Level.DEBUG);
        filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health/readiness"), response,
                (req, res) -> response.setStatus(503));
        assertThat(lastEvent().getLevel()).isEqualTo(Level.ERROR);
    }

    @Test
    void jsonEncoder_writesNumericFieldsAndCorrelationAsJson() throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/notes"), response,
                (req, res) -> {});
        var encoder = new StructuredLogEncoder();
        var context = new LoggerContext();
        context.putObject(Environment.class.getName(), new MockEnvironment()
                .withProperty("spring.application.name", "notes-backend")
                .withProperty("app.version", "test-version")
                .withProperty("app.environment", "test")
                .withProperty("HOSTNAME", "test-host")
                .withProperty("K8S_POD_NAME", "backend-test"));
        encoder.setContext(context);
        encoder.setFormat("com.notesapp.logging.JsonLogFormatter");
        encoder.start();
        try {
            var json = new ObjectMapper().readTree(encoder.encode(lastEvent()));
            assertThat(json.at("/event/action").asText()).as(json.toString()).isEqualTo("http.request.completed");
            assertThat(json.path("request_id").asText()).isEqualTo(response.getHeader("X-Request-Id"));
            assertThat(json.at("/http/response/status_code").isInt()).isTrue();
            assertThat(json.at("/http/response/status_code").asInt()).isEqualTo(200);
            assertThat(json.path("duration_ms").isNumber()).isTrue();
            assertThat(json.path("level").asText()).isEqualTo("INFO");
            assertThat(json.path("severity_number").asInt()).isEqualTo(9);
            assertThat(json.path("timestamp").asText()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z");
            assertThat(json.at("/service/name").asText()).isEqualTo("notes-backend");
            assertThat(json.at("/service/version").asText()).isEqualTo("test-version");
            assertThat(json.at("/deployment/environment/name").asText()).isEqualTo("test");
            assertThat(json.at("/host/name").asText()).isEqualTo("test-host");
            assertThat(json.at("/k8s/pod/name").asText()).isEqualTo("backend-test");
            assertThat(json.path("trace_id").asText()).matches("[0-9a-f]{32}");
            assertThat(json.has("error")).isFalse();
        } finally {
            encoder.stop();
            context.stop();
        }
    }

    @Test
    void incomingTraceContext_isPreservedAndRestoredAfterRequest() throws Exception {
        String traceId = "1234567890abcdef1234567890abcdef";
        var request = new MockHttpServletRequest("GET", "/api/notes");
        request.addHeader("traceparent", "00-" + traceId + "-1234567890abcdef-01");
        var response = new MockHttpServletResponse();
        MDC.put("trace_id", "previous");
        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("trace_id")).isEqualTo(traceId);
        });
        assertThat(lastEvent().getMDCPropertyMap()).containsEntry("trace_id", traceId);
        assertThat(response.getHeader("traceparent")).matches("00-" + traceId + "-[0-9a-f]{16}-01");
        assertThat(MDC.get("trace_id")).isEqualTo("previous");
    }

    @Test
    void invalidTraceContext_isReplaced() throws Exception {
        for (String header : new String[]{"invalid", "00-" + "0".repeat(32) + "-1234567890abcdef-01",
                "00-1234567890abcdef1234567890abcdef-0000000000000000-01"}) {
            var request = new MockHttpServletRequest("GET", "/api/notes");
            request.addHeader("traceparent", header);
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
            assertThat(lastEvent().getMDCPropertyMap().get("trace_id")).matches("[0-9a-f]{32}")
                    .isNotEqualTo("0".repeat(32));
        }
    }

    private ILoggingEvent lastEvent() {
        return appender.list.getLast();
    }

    private Map<String, Object> fields(ILoggingEvent event) {
        return event.getKeyValuePairs().stream().collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
    }
}
