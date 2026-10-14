package com.rha.lessontype;

import java.time.Instant;

public record LessonTypeResponse(Long id, String code, String name, int capacity, boolean active,
        Instant createdAt) {

    public static LessonTypeResponse from(LessonType type) {
        return new LessonTypeResponse(type.getId(), type.getCode(), type.getName(), type.getCapacity(),
                type.isActive(), type.getCreatedAt());
    }
}
