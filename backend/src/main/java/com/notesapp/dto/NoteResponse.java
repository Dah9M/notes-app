package com.notesapp.dto;

import com.notesapp.entity.Note;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

public record NoteResponse(
        Long id,
        String title,
        String content,
        Set<String> tags,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static NoteResponse from(Note note) {
        return new NoteResponse(
                note.getId(),
                note.getTitle(),
                note.getContent(),
                note.getTags().stream().map(t -> t.getName()).collect(Collectors.toSet()),
                note.getCreatedAt(),
                note.getUpdatedAt()
        );
    }
}
