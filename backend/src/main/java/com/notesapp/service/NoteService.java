package com.notesapp.service;

import com.notesapp.dto.NoteRequest;
import com.notesapp.entity.Note;
import com.notesapp.entity.Tag;
import com.notesapp.entity.User;
import com.notesapp.exception.ApiException;
import com.notesapp.repository.NoteRepository;
import com.notesapp.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class NoteService {

    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final TagService tagService;

    public NoteService(NoteRepository noteRepository, UserRepository userRepository, TagService tagService) {
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
        this.tagService = tagService;
    }

    public List<Note> findAll(Long ownerId, String tagFilter) {
        if (tagFilter == null || tagFilter.isBlank()) {
            return noteRepository.findByOwnerIdOrderByUpdatedAtDesc(ownerId);
        }
        return noteRepository.findByOwnerIdAndTagsNameOrderByUpdatedAtDesc(ownerId, tagFilter);
    }

    public Note findOne(Long id, Long ownerId) {
        return noteRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Note not found"));
    }

    public Note create(Long ownerId, NoteRequest request) {
        User owner = userRepository.getReferenceById(ownerId);
        Note note = new Note();
        note.setTitle(request.title());
        note.setContent(request.content());
        note.setOwner(owner);
        note.setTags(resolveTags(request.tags()));
        return noteRepository.save(note);
    }

    public Note update(Long id, Long ownerId, NoteRequest request) {
        Note note = findOne(id, ownerId);
        note.setTitle(request.title());
        note.setContent(request.content());
        note.setTags(resolveTags(request.tags()));
        return noteRepository.save(note);
    }

    public void delete(Long id, Long ownerId) {
        Note note = findOne(id, ownerId);
        noteRepository.delete(note);
    }

    private Set<Tag> resolveTags(Set<String> names) {
        if (names == null) {
            return new HashSet<>();
        }
        Set<Tag> tags = new HashSet<>();
        for (String name : names) {
            if (!name.isBlank()) {
                tags.add(tagService.findOrCreate(name.trim()));
            }
        }
        return tags;
    }
}
