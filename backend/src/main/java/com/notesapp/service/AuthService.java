package com.notesapp.service;

import com.notesapp.dto.LoginRequest;
import com.notesapp.dto.RegisterRequest;
import com.notesapp.entity.User;
import com.notesapp.exception.ApiException;
import com.notesapp.metrics.BusinessMetrics;
import com.notesapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final BusinessMetrics metrics;

    public User register(RegisterRequest request) {
        log.atDebug().addKeyValue("event.action", "user.register.started").log("Начата регистрация");
        if (userRepository.existsByUsername(request.username())) {
            log.atInfo().addKeyValue("event.action", "user.register.rejected")
                    .addKeyValue("reason", "username_taken").log("Регистрация отклонена");
            throw new ApiException(HttpStatus.CONFLICT, "Username is already taken");
        }
        User user = userRepository.save(new User(request.username(), request.password()));
        metrics.registrationSucceeded();
        log.atInfo().addKeyValue("event.action", "user.register.succeeded")
                .addKeyValue("userId", user.getId()).log("Пользователь зарегистрирован");
        return user;
    }

    public User login(LoginRequest request) {
        log.atDebug().addKeyValue("event.action", "user.login.started").log("Начат вход");
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> {
                    log.atInfo().addKeyValue("event.action", "user.login.rejected")
                            .addKeyValue("reason", "invalid_credentials").log("Вход отклонён");
                    return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
                });
        if (!user.getPassword().equals(request.password())) {
            log.atInfo().addKeyValue("event.action", "user.login.rejected")
                    .addKeyValue("reason", "invalid_credentials").log("Вход отклонён");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        metrics.loginSucceeded();
        log.atInfo().addKeyValue("event.action", "user.login.succeeded")
                .addKeyValue("userId", user.getId()).log("Пользователь вошёл");
        return user;
    }
}
