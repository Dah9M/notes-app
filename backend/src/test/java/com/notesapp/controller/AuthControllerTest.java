package com.notesapp.controller;

import com.notesapp.dto.LoginRequest;
import com.notesapp.dto.RegisterRequest;
import com.notesapp.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerTest extends AbstractControllerTest {

    @Test
    void register_thenLogin_succeedsWithSameUser() {
        ResponseEntity<UserResponse> registerResponse =
                restTemplate.postForEntity("/api/auth/register", new RegisterRequest("alice", "secret"), UserResponse.class);

        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registerResponse.getBody()).isNotNull();
        assertThat(registerResponse.getBody().username()).isEqualTo("alice");

        ResponseEntity<UserResponse> loginResponse =
                restTemplate.postForEntity("/api/auth/login", new LoginRequest("alice", "secret"), UserResponse.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody().id()).isEqualTo(registerResponse.getBody().id());
    }

    @Test
    void register_duplicateUsername_returnsConflict() {
        restTemplate.postForEntity("/api/auth/register", new RegisterRequest("alice", "secret"), UserResponse.class);

        ResponseEntity<Map> response =
                restTemplate.postForEntity("/api/auth/register", new RegisterRequest("alice", "another"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void register_tooShortPassword_returnsBadRequest() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/register", new RegisterRequest("alice", "x"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void login_unknownUsername_returnsUnauthorized() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest("no-such-user", "secret"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_wrongPassword_returnsUnauthorized() {
        restTemplate.postForEntity("/api/auth/register", new RegisterRequest("alice", "secret"), UserResponse.class);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest("alice", "wrong"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
