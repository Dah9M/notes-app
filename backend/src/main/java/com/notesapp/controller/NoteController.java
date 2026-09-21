package com.notesapp.controller;

import com.notesapp.auth.CurrentUser;
import com.notesapp.dto.NoteRequest;
import com.notesapp.dto.NoteResponse;
import com.notesapp.entity.Note;
import com.notesapp.service.NoteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping
    public List<NoteResponse> findAll(HttpServletRequest request,
                                       @RequestParam(required = false) String tag) {
        Long ownerId = CurrentUser.id(request);
        return noteService.findAll(ownerId, tag).stream().map(NoteResponse::from).toList();
    }

    @GetMapping("/{id}")
    public NoteResponse findOne(HttpServletRequest request, @PathVariable Long id) {
        return NoteResponse.from(noteService.findOne(id, CurrentUser.id(request)));
    }

    @PostMapping
    public ResponseEntity<NoteResponse> create(HttpServletRequest request,
                                                @Valid @RequestBody NoteRequest body) {
        Note note = noteService.create(CurrentUser.id(request), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(NoteResponse.from(note));
    }

    @PutMapping("/{id}")
    public NoteResponse update(HttpServletRequest request, @PathVariable Long id,
                                @Valid @RequestBody NoteRequest body) {
        return NoteResponse.from(noteService.update(id, CurrentUser.id(request), body));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(HttpServletRequest request, @PathVariable Long id) {
        noteService.delete(id, CurrentUser.id(request));
    }
}
