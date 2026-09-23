package com.notesapp.service;

import com.notesapp.dto.LoginRequest;
import com.notesapp.dto.RegisterRequest;
import com.notesapp.entity.User;
import com.notesapp.exception.ApiException;
import com.notesapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository);
    }

    @Test
    void register_savesNewUser() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        User result = authService.register(new RegisterRequest("alice", "secret"));

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUsername()).isEqualTo("alice");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_usernameTaken_throwsConflict() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("alice", "secret")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_correctCredentials_returnsUser() {
        User stored = new User("alice", "secret");
        stored.setId(1L);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(stored));

        User result = authService.login(new LoginRequest("alice", "secret"));

        assertThat(result).isSameAs(stored);
    }

    @Test
    void login_unknownUsername_throwsUnauthorized() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "secret")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        User stored = new User("alice", "secret");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
