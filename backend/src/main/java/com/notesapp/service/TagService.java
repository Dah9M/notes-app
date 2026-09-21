package com.notesapp.service;

import com.notesapp.dto.TagRequest;
import com.notesapp.entity.Tag;
import com.notesapp.exception.ApiException;
import com.notesapp.repository.TagRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TagService {

    private final TagRepository tagRepository;

    public TagService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    public List<Tag> findAll() {
        return tagRepository.findAll();
    }

    public Tag findById(Long id) {
        return tagRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tag not found"));
    }

    public Tag create(TagRequest request) {
        if (tagRepository.existsByName(request.name())) {
            throw new ApiException(HttpStatus.CONFLICT, "Tag already exists");
        }
        return tagRepository.save(new Tag(request.name()));
    }

    public Tag update(Long id, TagRequest request) {
        Tag tag = findById(id);
        tag.setName(request.name());
        return tagRepository.save(tag);
    }

    public void delete(Long id) {
        Tag tag = findById(id);
        tagRepository.delete(tag);
    }

    public Tag findOrCreate(String name) {
        return tagRepository.findByName(name)
                .orElseGet(() -> tagRepository.save(new Tag(name)));
    }
}
