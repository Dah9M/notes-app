package com.notesapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record NoteRequest(
        @NotBlank @Size(max = 256) String title,
        String content,
        Set<String> tags
) {
}
