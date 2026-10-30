package com.notesapp.controller;

import com.notesapp.dto.LoginRequest;
import com.notesapp.dto.NoteRequest;
import com.notesapp.dto.NoteResponse;
import com.notesapp.dto.RegisterRequest;
import com.notesapp.dto.UserResponse;
import com.notesapp.repository.UserRepository;
import com.notesapp.service.AuthService;
import com.notesapp.service.NoteService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureObservability
class MetricsControllerTest extends AbstractControllerTest {

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private AuthService authService;

    @Autowired
    private NoteService noteService;

    @Autowired
    private UserRepository users;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private double count(String name) {
        return registry.get(name).counter().count();
    }

    private HttpEntity<?> authed(Long userId, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", userId.toString());
        return new HttpEntity<>(body, headers);
    }

    @Test
    void exportsOpenMetricsWithoutAuthWithRuntimeHttpAndAllBusinessCounters() {
        restTemplate.getForEntity("/api/notes", String.class);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/openmetrics-text;version=1.0.0;charset=utf-8");

        var response = restTemplate.exchange(
                "http://localhost:" + managementPort + "/actuator/prometheus",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("Content-Type"))
                .startsWith("application/openmetrics-text");
        assertThat(response.getBody()).contains(
                "jvm_memory_used_bytes", "http_server_requests_seconds_count",
                "application=\"notes-backend\"",
                "notesapp_registrations_total", "notesapp_logins_total",
                "notesapp_notes_creations_total", "notesapp_notes_updated_total",
                "notesapp_notes_deleted_total").endsWith("# EOF\n");

        var plain = restTemplate.getForEntity(
                "http://localhost:" + managementPort + "/actuator/prometheus", String.class);
        assertThat(plain.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(plain.getHeaders().getFirst("Content-Type")).startsWith("text/plain");
        assertThat(plain.getBody()).contains("notesapp_notes_creations_total", "jvm_memory_used_bytes");
    }

    @Test
    void metricsAreNotExposedOnPublicApiPortAndHealthProbesStillWork() {
        var publicResponse = restTemplate.getForEntity("/actuator/prometheus", String.class);
        assertThat(publicResponse.getStatusCode().is2xxSuccessful()).isFalse();
        assertThat(publicResponse.getBody()).doesNotContain("jvm_memory_used_bytes", "notesapp_notes_creations_total");
        assertThat(restTemplate.getForEntity("/livez", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.getForEntity("/readyz", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.getForEntity(
                "http://localhost:" + managementPort + "/actuator/health/readiness", String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void realHttpOperationsIncrementEachCounterOnceAndRejectsDoNotIncrement() {
        double registrations = count("notesapp.registrations");
        double logins = count("notesapp.logins");
        double created = count("notesapp.notes.creations");
        double updated = count("notesapp.notes.updated");
        double deleted = count("notesapp.notes.deleted");
        var credentials = new RegisterRequest("metrics-user", "secret");
        var registration = restTemplate.postForEntity("/api/auth/register", credentials, UserResponse.class);
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long userId = registration.getBody().id();
        assertThat(restTemplate.postForEntity("/api/auth/register", credentials, String.class).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(restTemplate.postForEntity("/api/auth/login",
                new LoginRequest("metrics-user", "wrong"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.postForEntity("/api/auth/login",
                new LoginRequest("metrics-user", "secret"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        var body = new NoteRequest("Metrics note", "Body", Set.of());
        assertThat(restTemplate.postForEntity("/api/notes", body, String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        var note = restTemplate.exchange("/api/notes", HttpMethod.POST, authed(userId, body), NoteResponse.class);
        assertThat(note.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String path = "/api/notes/" + note.getBody().id();
        assertThat(restTemplate.exchange(path, HttpMethod.PUT,
                authed(userId, new NoteRequest("Updated", "Body", Set.of())), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(path, HttpMethod.PUT,
                authed(userId, new NoteRequest("", "Body", Set.of())), String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(restTemplate.exchange(path, HttpMethod.DELETE, authed(userId, null), Void.class)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(restTemplate.exchange(path, HttpMethod.DELETE, authed(userId, null), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(count("notesapp.registrations")).isEqualTo(registrations + 1);
        assertThat(count("notesapp.logins")).isEqualTo(logins + 1);
        assertThat(count("notesapp.notes.creations")).isEqualTo(created + 1);
        assertThat(count("notesapp.notes.updated")).isEqualTo(updated + 1);
        assertThat(count("notesapp.notes.deleted")).isEqualTo(deleted + 1);
    }

    @Test
    void enclosingRollbackDoesNotCountSuccessfulRepositoryCalls() {
        double registrations = count("notesapp.registrations");
        double created = count("notesapp.notes.creations");
        double updated = count("notesapp.notes.updated");
        double deleted = count("notesapp.notes.deleted");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var user = authService.register(new RegisterRequest("rolled-back", "secret"));
            var body = new NoteRequest("Rollback", "Body", Set.of());
            var note = noteService.create(user.getId(), body);
            noteService.update(note.getId(), user.getId(), body);
            noteService.delete(note.getId(), user.getId());
            assertThat(count("notesapp.registrations")).isEqualTo(registrations);
            assertThat(count("notesapp.notes.creations")).isEqualTo(created);
            status.setRollbackOnly();
        });

        assertThat(users.existsByUsername("rolled-back")).isFalse();
        assertThat(count("notesapp.registrations")).isEqualTo(registrations);
        assertThat(count("notesapp.notes.creations")).isEqualTo(created);
        assertThat(count("notesapp.notes.updated")).isEqualTo(updated);
        assertThat(count("notesapp.notes.deleted")).isEqualTo(deleted);
    }

    @Test
    void enclosingCommitCountsOnlyAfterCommit() {
        double before = count("notesapp.registrations");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            authService.register(new RegisterRequest("committed-user", "secret"));
            assertThat(count("notesapp.registrations")).isEqualTo(before);
        });
        assertThat(users.existsByUsername("committed-user")).isTrue();
        assertThat(count("notesapp.registrations")).isEqualTo(before + 1);
    }
}
