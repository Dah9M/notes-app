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
        List<Tag> tags = tagRepository.findAll();
        log.atDebug().addKeyValue("event.action", "tag.list")
                .addKeyValue("resultCount", tags.size()).log("Получен список тегов");
        return tags;
    }

    public Tag findById(Long id) {
        log.atTrace().addKeyValue("event.action", "tag.lookup")
                .addKeyValue("tagId", id).log("Поиск тега");
        return tagRepository.findById(id)
                .orElseThrow(() -> {
                    log.atDebug().addKeyValue("event.action", "tag.not_found")
                            .addKeyValue("tagId", id).log("Тег не найден");
                    return new ApiException(HttpStatus.NOT_FOUND, "Tag not found");
                });
    }

    public Tag create(TagRequest request) {
        if (tagRepository.existsByName(request.name())) {
            log.atDebug().addKeyValue("event.action", "tag.create.rejected")
                    .addKeyValue("reason", "already_exists").log("Создание тега отклонено");
            throw new ApiException(HttpStatus.CONFLICT, "Tag already exists");
        }
        Tag tag = tagRepository.save(new Tag(request.name()));
        log.atInfo().addKeyValue("event.action", "tag.created")
                .addKeyValue("tagId", tag.getId()).log("Тег создан");
        return tag;
    }

    public Tag update(Long id, TagRequest request) {
        Tag tag = findById(id);
        tag.setName(request.name());
        tag = tagRepository.save(tag);
        log.atInfo().addKeyValue("event.action", "tag.updated")
                .addKeyValue("tagId", tag.getId()).log("Тег обновлён");
        return tag;
    }

    public void delete(Long id) {
        Tag tag = findById(id);
        tagRepository.delete(tag);
        log.atInfo().addKeyValue("event.action", "tag.deleted")
                .addKeyValue("tagId", id).log("Тег удалён");
    }

    public Tag findOrCreate(String name) {
        log.atTrace().addKeyValue("event.action", "tag.resolve").log("Разрешение тега по имени");
        return tagRepository.findByName(name)
                .orElseGet(() -> {
                    Tag tag = tagRepository.save(new Tag(name));
                    log.atDebug().addKeyValue("event.action", "tag.auto_created")
                            .addKeyValue("tagId", tag.getId()).log("Тег создан при привязке к заметке");
                    return tag;
                });
    }
}
