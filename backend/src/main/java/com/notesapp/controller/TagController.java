package com.notesapp.controller;

import com.notesapp.dto.TagRequest;
import com.notesapp.dto.TagResponse;
import com.notesapp.entity.Tag;
import com.notesapp.service.TagService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    public List<TagResponse> findAll() {
        return tagService.findAll().stream().map(TagResponse::from).toList();
    }

    @GetMapping("/{id}")
    public TagResponse findOne(@PathVariable Long id) {
        return TagResponse.from(tagService.findById(id));
    }

    @PostMapping
    public ResponseEntity<TagResponse> create(@Valid @RequestBody TagRequest request) {
        Tag tag = tagService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(TagResponse.from(tag));
    }

    @PutMapping("/{id}")
    public TagResponse update(@PathVariable Long id, @Valid @RequestBody TagRequest request) {
        return TagResponse.from(tagService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        tagService.delete(id);
    }
}
