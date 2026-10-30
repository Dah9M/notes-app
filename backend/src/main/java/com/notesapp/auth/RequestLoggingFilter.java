package com.notesapp.auth;

import com.notesapp.logging.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        String requestId = UUID.randomUUID().toString();
        MDC.remove("userId");
        MDC.put("request_id", requestId);
        TraceContext trace = TraceContext.from(request.getHeader("traceparent"));
        MDC.put("trace_id", trace.traceId());
        MDC.put("http.request.method", request.getMethod());
        response.setHeader("X-Request-Id", requestId);
        response.setHeader("traceparent", trace.parent());
        long startedAt = System.nanoTime();
        boolean failed = false;
        try {
            log.atTrace().addKeyValue("event.action", "http.request.started").log("Получен HTTP-запрос");
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException | Error ex) {
            failed = true;
            log.atError().addKeyValue("event.action", "http.request.failed")
                    .setCause(ex).log("Ошибка вне обработчика REST API");
            throw ex;
        } finally {
            try {
                int status = failed ? 500 : response.getStatus();
                // Route templates avoid recording private data from raw URLs and query strings.
                Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                Object userId = request.getAttribute(AuthInterceptor.USER_ID_ATTRIBUTE);
                if (userId != null) {
                    MDC.put("userId", userId.toString());
                }
                boolean healthCheck = request.getRequestURI().startsWith("/actuator/health");
                var event = status >= 500 ? log.atError() : healthCheck && status < 400 ? log.atDebug() : log.atInfo();
                event.addKeyValue("event.action", "http.request.completed")
                        .addKeyValue("http.route", route == null ? "unmatched" : route.toString())
                        .addKeyValue("http.response.status_code", status)
                        .addKeyValue("duration_ms", (System.nanoTime() - startedAt) / 1_000_000.0)
                        .addKeyValue("event.outcome", status >= 400 ? "failure" : "success")
                        .log("HTTP-запрос завершён");
            } finally {
                if (previousContext == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previousContext);
                }
            }
        }
    }
}
