package com.notesapp.controller;

import com.notesapp.dto.RegisterRequest;
import com.notesapp.dto.TagRequest;
import com.notesapp.dto.TagResponse;
import com.notesapp.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TagControllerTest extends AbstractControllerTest {

    private Long registerUser() {
        ResponseEntity<UserResponse> response =
                restTemplate.postForEntity("/api/auth/register", new RegisterRequest("alice", "secret"), UserResponse.class);
        return response.getBody().id();
    }

    private HttpEntity<?> authed(Long userId, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(userId));
        return new HttpEntity<>(body, headers);
    }

    @Test
    void listTags_withoutAuthHeader_returnsUnauthorized() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/api/tags", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createTag_thenAppearsInList() {
        Long userId = registerUser();

        ResponseEntity<TagResponse> created = restTemplate.exchange(
                "/api/tags", HttpMethod.POST, authed(userId, new TagRequest("work")), TagResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<TagResponse[]> list = restTemplate.exchange(
                "/api/tags", HttpMethod.GET, authed(userId, null), TagResponse[].class);
        assertThat(list.getBody()).extracting(TagResponse::name).containsExactly("work");
    }

    @Test
    void createTag_duplicateName_returnsConflict() {
        Long userId = registerUser();
        restTemplate.exchange("/api/tags", HttpMethod.POST, authed(userId, new TagRequest("work")), TagResponse.class);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/tags", HttpMethod.POST, authed(userId, new TagRequest("work")), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void getTag_missing_returnsNotFound() {
        Long userId = registerUser();

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/tags/999999", HttpMethod.GET, authed(userId, null), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateTag_changesName() {
        Long userId = registerUser();
        ResponseEntity<TagResponse> created = restTemplate.exchange(
                "/api/tags", HttpMethod.POST, authed(userId, new TagRequest("work")), TagResponse.class);
        Long tagId = created.getBody().id();

        ResponseEntity<TagResponse> updated = restTemplate.exchange(
                "/api/tags/" + tagId, HttpMethod.PUT, authed(userId, new TagRequest("home")), TagResponse.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().name()).isEqualTo("home");
    }

    @Test
    void deleteTag_thenGetReturnsNotFound() {
        Long userId = registerUser();
        ResponseEntity<TagResponse> created = restTemplate.exchange(
                "/api/tags", HttpMethod.POST, authed(userId, new TagRequest("work")), TagResponse.class);
        Long tagId = created.getBody().id();

        ResponseEntity<Void> deleted = restTemplate.exchange(
                "/api/tags/" + tagId, HttpMethod.DELETE, authed(userId, null), Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> getAfterDelete = restTemplate.exchange(
                "/api/tags/" + tagId, HttpMethod.GET, authed(userId, null), Map.class);
        assertThat(getAfterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
