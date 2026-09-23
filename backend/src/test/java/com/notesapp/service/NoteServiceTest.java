package com.notesapp.service;

import com.notesapp.dto.NoteRequest;
import com.notesapp.entity.Note;
import com.notesapp.entity.Tag;
import com.notesapp.entity.User;
import com.notesapp.exception.ApiException;
import com.notesapp.repository.NoteRepository;
import com.notesapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock
    private NoteRepository noteRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TagService tagService;

    private NoteService noteService;

    @BeforeEach
    void setUp() {
        noteService = new NoteService(noteRepository, userRepository, tagService);
    }

    @Test
    void findAll_withoutTagFilter_usesOwnerQuery() {
        Note note = new Note();
        when(noteRepository.findByOwnerIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of(note));

        List<Note> result = noteService.findAll(1L, null);

        assertThat(result).containsExactly(note);
        verify(noteRepository, never()).findByOwnerIdAndTagsNameOrderByUpdatedAtDesc(any(), any());
    }

    @Test
    void findAll_withTagFilter_usesTagQuery() {
        Note note = new Note();
        when(noteRepository.findByOwnerIdAndTagsNameOrderByUpdatedAtDesc(1L, "work")).thenReturn(List.of(note));

        List<Note> result = noteService.findAll(1L, "work");

        assertThat(result).containsExactly(note);
        verify(noteRepository, never()).findByOwnerIdOrderByUpdatedAtDesc(any());
    }

    @Test
    void findOne_notOwnedOrMissing_throwsNotFound() {
        when(noteRepository.findByIdAndOwnerId(5L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.findOne(5L, 1L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void create_resolvesTagsAndSetsOwner() {
        User owner = new User("alice", "secret");
        owner.setId(1L);
        when(userRepository.getReferenceById(1L)).thenReturn(owner);
        when(tagService.findOrCreate("work")).thenReturn(new Tag("work"));
        when(tagService.findOrCreate("idea")).thenReturn(new Tag("idea"));
        when(noteRepository.save(any(Note.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Note result = noteService.create(1L, new NoteRequest("Title", "Body", Set.of("work", "idea", "")));

        assertThat(result.getTitle()).isEqualTo("Title");
        assertThat(result.getContent()).isEqualTo("Body");
        assertThat(result.getOwner()).isSameAs(owner);
        assertThat(result.getTags()).extracting(Tag::getName).containsExactlyInAnyOrder("work", "idea");
        verify(tagService, never()).findOrCreate("");
    }

    @Test
    void create_withNullTags_savesEmptyTagSet() {
        when(userRepository.getReferenceById(1L)).thenReturn(new User("alice", "secret"));
        when(noteRepository.save(any(Note.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Note result = noteService.create(1L, new NoteRequest("Title", "Body", null));

        assertThat(result.getTags()).isEmpty();
        verifyNoInteractions(tagService);
    }

    @Test
    void update_missingNote_throwsNotFound() {
        when(noteRepository.findByIdAndOwnerId(5L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.update(5L, 1L, new NoteRequest("T", "C", Set.of())))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(noteRepository, never()).save(any());
    }

    @Test
    void update_existingNote_updatesFieldsAndTags() {
        Note existing = new Note();
        existing.setTitle("Old");
        when(noteRepository.findByIdAndOwnerId(1L, 1L)).thenReturn(Optional.of(existing));
        when(tagService.findOrCreate("new-tag")).thenReturn(new Tag("new-tag"));
        when(noteRepository.save(any(Note.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Note result = noteService.update(1L, 1L, new NoteRequest("New", "Body", Set.of("new-tag")));

        assertThat(result.getTitle()).isEqualTo("New");
        assertThat(result.getContent()).isEqualTo("Body");
        assertThat(result.getTags()).extracting(Tag::getName).containsExactly("new-tag");
    }

    @Test
    void delete_existingNote_removesIt() {
        Note existing = new Note();
        when(noteRepository.findByIdAndOwnerId(1L, 1L)).thenReturn(Optional.of(existing));

        noteService.delete(1L, 1L);

        verify(noteRepository).delete(existing);
    }

    @Test
    void delete_missingNote_throwsNotFoundAndDoesNotCallDelete() {
        when(noteRepository.findByIdAndOwnerId(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.delete(1L, 1L)).isInstanceOf(ApiException.class);

        verify(noteRepository, never()).delete(any());
    }
}
