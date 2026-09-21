package com.notesapp.repository;

import com.notesapp.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NoteRepository extends JpaRepository<Note, Long> {

    List<Note> findByOwnerIdOrderByUpdatedAtDesc(Long ownerId);

    List<Note> findByOwnerIdAndTagsNameOrderByUpdatedAtDesc(Long ownerId, String tagName);

    Optional<Note> findByIdAndOwnerId(Long id, Long ownerId);
}
