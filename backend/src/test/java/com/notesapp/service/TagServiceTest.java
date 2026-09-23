package com.notesapp.service;

import com.notesapp.dto.TagRequest;
import com.notesapp.entity.Tag;
import com.notesapp.exception.ApiException;
import com.notesapp.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;

    private TagService tagService;

    @BeforeEach
    void setUp() {
        tagService = new TagService(tagRepository);
    }

    @Test
    void findAll_returnsAllTags() {
        Tag work = new Tag("work");
        when(tagRepository.findAll()).thenReturn(List.of(work));

        assertThat(tagService.findAll()).containsExactly(work);
    }

    @Test
    void findById_missing_throwsNotFound() {
        when(tagRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tagService.findById(42L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void create_newName_savesTag() {
        when(tagRepository.existsByName("work")).thenReturn(false);
        when(tagRepository.save(any(Tag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Tag result = tagService.create(new TagRequest("work"));

        assertThat(result.getName()).isEqualTo("work");
    }

    @Test
    void create_duplicateName_throwsConflict() {
        when(tagRepository.existsByName("work")).thenReturn(true);

        assertThatThrownBy(() -> tagService.create(new TagRequest("work")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verify(tagRepository, never()).save(any());
    }

    @Test
    void findOrCreate_existingName_returnsExistingWithoutSaving() {
        Tag existing = new Tag("work");
        when(tagRepository.findByName("work")).thenReturn(Optional.of(existing));

        Tag result = tagService.findOrCreate("work");

        assertThat(result).isSameAs(existing);
        verify(tagRepository, never()).save(any());
    }

    @Test
    void findOrCreate_newName_savesAndReturnsIt() {
        when(tagRepository.findByName("idea")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Tag result = tagService.findOrCreate("idea");

        assertThat(result.getName()).isEqualTo("idea");
        verify(tagRepository).save(any(Tag.class));
    }

    @Test
    void delete_removesTag() {
        Tag tag = new Tag("work");
        when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));

        tagService.delete(1L);

        verify(tagRepository).delete(tag);
    }
}
