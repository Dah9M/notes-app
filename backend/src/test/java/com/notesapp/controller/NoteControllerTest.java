package com.notesapp.controller;

import com.notesapp.dto.NoteRequest;
import com.notesapp.dto.NoteResponse;
import com.notesapp.dto.RegisterRequest;
import com.notesapp.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NoteControllerTest extends AbstractControllerTest {

    private Long registerUser(String username) {
        ResponseEntity<UserResponse> response =
                restTemplate.postForEntity("/api/auth/register", new RegisterRequest(username, "secret"), UserResponse.class);
        return response.getBody().id();
    }

    private HttpEntity<?> authed(Long userId, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(userId));
        return new HttpEntity<>(body, headers);
    }

    @Test
    void createNote_withoutAuthHeader_returnsUnauthorized() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/notes", new NoteRequest("Title", "Body", Set.of()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createNote_withUnknownUserId_returnsUnauthorized() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/notes", HttpMethod.POST,
                authed(999_999L, new NoteRequest("Title", "Body", Set.of())), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createNote_thenAppearsInOwnersList() {
        Long userId = registerUser("alice");

        ResponseEntity<NoteResponse> created = restTemplate.exchange(
                "/api/notes", HttpMethod.POST,
                authed(userId, new NoteRequest("Title", "Body", Set.of("work"))), NoteResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().tags()).containsExactly("work");

        ResponseEntity<NoteResponse[]> list = restTemplate.exchange(
                "/api/notes", HttpMethod.GET, authed(userId, null), NoteResponse[].class);

        assertThat(list.getBody()).extracting(NoteResponse::id).contains(created.getBody().id());
    }

    @Test
    void getNote_ownedByAnotherUser_returnsNotFound() {
        Long ownerId = registerUser("alice");
        Long intruderId = registerUser("bob");

        ResponseEntity<NoteResponse> created = restTemplate.exchange(
                "/api/notes", HttpMethod.POST,
                authed(ownerId, new NoteRequest("Secret", "Body", Set.of())), NoteResponse.class);
        Long noteId = created.getBody().id();

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/notes/" + noteId, HttpMethod.GET, authed(intruderId, null), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateNote_changesFieldsAndTags() {
        Long userId = registerUser("alice");
        ResponseEntity<NoteResponse> created = restTemplate.exchange(
                "/api/notes", HttpMethod.POST,
                authed(userId, new NoteRequest("Old", "Old body", Set.of("draft"))), NoteResponse.class);
        Long noteId = created.getBody().id();

        ResponseEntity<NoteResponse> updated = restTemplate.exchange(
                "/api/notes/" + noteId, HttpMethod.PUT,
                authed(userId, new NoteRequest("New", "New body", Set.of("done"))), NoteResponse.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().title()).isEqualTo("New");
        assertThat(updated.getBody().tags()).containsExactly("done");
    }

    @Test
    void deleteNote_thenGetReturnsNotFound() {
        Long userId = registerUser("alice");
        ResponseEntity<NoteResponse> created = restTemplate.exchange(
                "/api/notes", HttpMethod.POST,
                authed(userId, new NoteRequest("Title", "Body", Set.of())), NoteResponse.class);
        Long noteId = created.getBody().id();

        ResponseEntity<Void> deleted = restTemplate.exchange(
                "/api/notes/" + noteId, HttpMethod.DELETE, authed(userId, null), Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> getAfterDelete = restTemplate.exchange(
                "/api/notes/" + noteId, HttpMethod.GET, authed(userId, null), Map.class);
        assertThat(getAfterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void filterByTag_returnsOnlyMatchingNotes() {
        Long userId = registerUser("alice");
        restTemplate.exchange("/api/notes", HttpMethod.POST,
                authed(userId, new NoteRequest("Work note", "x", Set.of("work"))), NoteResponse.class);
        restTemplate.exchange("/api/notes", HttpMethod.POST,
                authed(userId, new NoteRequest("Home note", "x", Set.of("home"))), NoteResponse.class);

        ResponseEntity<NoteResponse[]> filtered = restTemplate.exchange(
                "/api/notes?tag=work", HttpMethod.GET, authed(userId, null), NoteResponse[].class);

        assertThat(filtered.getBody()).extracting(NoteResponse::title).containsExactly("Work note");
    }

    @Test
    void optionsPreflight_toProtectedEndpoint_succeedsWithoutAuthHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Origin", "http://localhost:8081");
        headers.set("Access-Control-Request-Method", "POST");
        headers.set("Access-Control-Request-Headers", "content-type,x-user-id");

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/notes", HttpMethod.OPTIONS, new HttpEntity<>(headers), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
