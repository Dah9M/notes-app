package com.notesapp.auth;

import com.notesapp.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthInterceptorTest {

    @Mock
    private UserRepository userRepository;

    private AuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AuthInterceptor(userRepository);
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void preHandle_optionsRequest_alwaysAllowed() {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/notes");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void preHandle_missingHeader_returns401() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notes");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void preHandle_nonNumericHeader_returns401() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notes");
        request.addHeader(AuthInterceptor.USER_ID_HEADER, "not-a-number");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void preHandle_unknownUserId_returns401() {
        when(userRepository.existsById(99L)).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notes");
        request.addHeader(AuthInterceptor.USER_ID_HEADER, "99");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void preHandle_validUserId_allowsAndSetsAttributeAndMdc() {
        when(userRepository.existsById(1L)).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notes");
        request.addHeader(AuthInterceptor.USER_ID_HEADER, "1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        assertThat(request.getAttribute(AuthInterceptor.USER_ID_ATTRIBUTE)).isEqualTo(1L);
        assertThat(MDC.get("userId")).isEqualTo("1");
    }

    @Test
    void afterCompletion_clearsMdc() {
        MDC.put("userId", "1");

        interceptor.afterCompletion(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), null);

        assertThat(MDC.get("userId")).isNull();
    }
}
