package com.notesapp.auth;

import com.notesapp.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;

@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ID_ATTRIBUTE = "userId";
    private static final String USER_ID_MDC_KEY = "userId";

    private final UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String header = request.getHeader(USER_ID_HEADER);
        Long userId = parse(header);

        if (userId == null) {
            log.warn("Отклонён запрос без корректного заголовка X-User-Id: {} {}", request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        if (!userRepository.existsById(userId)) {
            log.warn("Отклонён запрос: пользователь с id={} не найден: {} {}", userId, request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        request.setAttribute(USER_ID_ATTRIBUTE, userId);
        MDC.put(USER_ID_MDC_KEY, String.valueOf(userId));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        MDC.remove(USER_ID_MDC_KEY);
    }

    private Long parse(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(header.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
