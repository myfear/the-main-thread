package com.themainthread.time;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import com.fasterxml.jackson.databind.ObjectMapper;

@ApplicationScoped
public class LessonCatalog {
    private final List<Lesson> lessons;

    public LessonCatalog(ObjectMapper mapper) {
        try (var stream = LessonCatalog.class.getResourceAsStream("/lessons.json")) {
            if (stream == null) {
                throw new IllegalStateException("Missing lesson catalogue");
            }
            lessons = List.copyOf(Arrays.asList(mapper.readValue(stream, Lesson[].class)));
            if (lessons.size() != 24) {
                throw new IllegalStateException("The calendar requires exactly 24 lessons");
            }
            for (int index = 0; index < lessons.size(); index++) {
                if (lessons.get(index).id() != index + 1) {
                    throw new IllegalStateException("Lessons must be ordered from 1 through 24");
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read the lesson catalogue", exception);
        }
    }

    public List<Lesson> all() {
        return lessons;
    }

    public Lesson get(int id) {
        if (id < 1 || id > lessons.size()) {
            throw new NotFoundException("Choose a door from 1 to 24.");
        }
        return lessons.get(id - 1);
    }

    public record Lesson(int id, String title, String summary, String challenge, String defaultLabel,
            String alternativeLabel, String reference, String code, boolean deep, boolean database) {
    }
}
