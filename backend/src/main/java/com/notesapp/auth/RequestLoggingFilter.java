package com.notesapp.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String REQUEST_ID_MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        long startedAt = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startedAt;
            log.info("Обработан запрос: {} {} -> статус {} ({} мс)",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }
}
