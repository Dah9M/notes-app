package com.notesapp.service;

import com.notesapp.dto.TagRequest;
import com.notesapp.entity.Tag;
import com.notesapp.exception.ApiException;
import com.notesapp.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TagService {

    private static final Logger log = LoggerFactory.getLogger(TagService.class);

    private final TagRepository tagRepository;

    public List<Tag> findAll() {
        return tagRepository.findAll();
    }

    public Tag findById(Long id) {
        return tagRepository.findById(id)
                .orElseThrow(() -> {
                    log.debug("Тег не найден: id={}", id);
                    return new ApiException(HttpStatus.NOT_FOUND, "Tag not found");
                });
    }

    public Tag create(TagRequest request) {
        if (tagRepository.existsByName(request.name())) {
            log.warn("Создание тега отклонено: тег '{}' уже существует", request.name());
            throw new ApiException(HttpStatus.CONFLICT, "Tag already exists");
        }
        Tag tag = tagRepository.save(new Tag(request.name()));
        log.info("Тег создан: id={} name={}", tag.getId(), tag.getName());
        return tag;
    }

    public Tag update(Long id, TagRequest request) {
        Tag tag = findById(id);
        tag.setName(request.name());
        tag = tagRepository.save(tag);
        log.info("Тег обновлён: id={} name={}", tag.getId(), tag.getName());
        return tag;
    }

    public void delete(Long id) {
        Tag tag = findById(id);
        tagRepository.delete(tag);
        log.info("Тег удалён: id={} name={}", tag.getId(), tag.getName());
    }

    public Tag findOrCreate(String name) {
        return tagRepository.findByName(name)
                .orElseGet(() -> {
                    Tag tag = tagRepository.save(new Tag(name));
                    log.debug("Тег автоматически создан при привязке к заметке: id={} name={}", tag.getId(), tag.getName());
                    return tag;
                });
    }
}
