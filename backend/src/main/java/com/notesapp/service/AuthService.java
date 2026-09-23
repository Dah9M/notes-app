package com.notesapp.service;

import com.notesapp.dto.LoginRequest;
import com.notesapp.dto.RegisterRequest;
import com.notesapp.entity.User;
import com.notesapp.exception.ApiException;
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

    public User register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            log.warn("Регистрация отклонена: логин '{}' уже занят", request.username());
            throw new ApiException(HttpStatus.CONFLICT, "Username is already taken");
        }
        User user = userRepository.save(new User(request.username(), request.password()));
        log.info("Пользователь зарегистрирован: id={} username={}", user.getId(), user.getUsername());
        return user;
    }

    public User login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> {
                    log.warn("Вход отклонён: логин '{}' не найден", request.username());
                    return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
                });
        if (!user.getPassword().equals(request.password())) {
            log.warn("Вход отклонён: неверный пароль для логина '{}'", request.username());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        log.info("Пользователь вошёл: id={} username={}", user.getId(), user.getUsername());
        return user;
    }
}
