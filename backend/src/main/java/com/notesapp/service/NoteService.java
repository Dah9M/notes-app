package com.notesapp.service;

import com.notesapp.dto.NoteRequest;
import com.notesapp.entity.Note;
import com.notesapp.entity.Tag;
import com.notesapp.entity.User;
import com.notesapp.exception.ApiException;
import com.notesapp.metrics.BusinessMetrics;
import com.notesapp.repository.NoteRepository;
import com.notesapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NoteService {

    private static final Logger log = LoggerFactory.getLogger(NoteService.class);

    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final TagService tagService;
    private final BusinessMetrics metrics;

    public List<Note> findAll(Long ownerId, String tagFilter) {
        boolean filtered = tagFilter != null && !tagFilter.isBlank();
        List<Note> notes = filtered
                ? noteRepository.findByOwnerIdAndTagsNameOrderByUpdatedAtDesc(ownerId, tagFilter)
                : noteRepository.findByOwnerIdOrderByUpdatedAtDesc(ownerId);
        log.atDebug().addKeyValue("event.action", "note.list")
                .addKeyValue("ownerId", ownerId).addKeyValue("filtered", filtered)
                .addKeyValue("resultCount", notes.size()).log("Получен список заметок");
        return notes;
    }

    public Note findOne(Long id, Long ownerId) {
        log.atTrace().addKeyValue("event.action", "note.lookup")
                .addKeyValue("noteId", id).addKeyValue("ownerId", ownerId).log("Поиск заметки владельца");
        return noteRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> {
                    log.atDebug().addKeyValue("event.action", "note.not_found")
                            .addKeyValue("noteId", id).addKeyValue("ownerId", ownerId).log("Заметка не найдена");
                    return new ApiException(HttpStatus.NOT_FOUND, "Note not found");
                });
    }

    public Note create(Long ownerId, NoteRequest request) {
        User owner = userRepository.getReferenceById(ownerId);
        Note note = new Note();
        note.setTitle(request.title());
        note.setContent(request.content());
        note.setOwner(owner);
        note.setTags(resolveTags(request.tags()));
        note = noteRepository.save(note);
        metrics.noteCreated();
        log.atInfo().addKeyValue("event.action", "note.created")
                .addKeyValue("noteId", note.getId()).addKeyValue("ownerId", ownerId)
                .addKeyValue("tagCount", note.getTags().size()).log("Заметка создана");
        return note;
    }

    public Note update(Long id, Long ownerId, NoteRequest request) {
        Note note = findOne(id, ownerId);
        note.setTitle(request.title());
        note.setContent(request.content());
        note.setTags(resolveTags(request.tags()));
        note = noteRepository.save(note);
        metrics.noteUpdated();
        log.atInfo().addKeyValue("event.action", "note.updated")
                .addKeyValue("noteId", note.getId()).addKeyValue("ownerId", ownerId)
                .addKeyValue("tagCount", note.getTags().size()).log("Заметка обновлена");
        return note;
    }

    public void delete(Long id, Long ownerId) {
        Note note = findOne(id, ownerId);
        noteRepository.delete(note);
        metrics.noteDeleted();
        log.atInfo().addKeyValue("event.action", "note.deleted")
                .addKeyValue("noteId", id).addKeyValue("ownerId", ownerId).log("Заметка удалена");
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
        log.atTrace().addKeyValue("event.action", "note.tags.resolved")
                .addKeyValue("inputCount", names.size()).addKeyValue("tagCount", tags.size())
                .log("Теги заметки разрешены");
        return tags;
    }
}
