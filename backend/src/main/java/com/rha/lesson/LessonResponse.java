package com.rha.lesson;

import com.rha.lessontype.LessonType;
import com.rha.member.Member;
import com.rha.room.Room;
import java.time.Instant;

public record LessonResponse(Long id, String title, LessonTypeSummary lessonType, InstructorSummary instructor,
        RoomSummary room, Instant startAt, Instant endAt, int capacity, int reservedCount, LessonStatus status,
        Instant publishAt, Instant canceledAt, String cancelReason, Instant createdAt) {

    public record LessonTypeSummary(Long id, String code, String name) {
    }

    public record InstructorSummary(Long id, String name) {
    }

    public record RoomSummary(Long id, String name) {
    }

    /** {@code reservedCount}는 취소되지 않은 예약 수이다. */
    static LessonResponse of(Lesson lesson, LessonType type, Member instructor, Room room, int reservedCount) {
        return new LessonResponse(lesson.getId(), lesson.getTitle(),
                new LessonTypeSummary(type.getId(), type.getCode(), type.getName()),
                new InstructorSummary(instructor.getId(), instructor.getName()),
                new RoomSummary(room.getId(), room.getName()),
                lesson.getStartAt(), lesson.getEndAt(), lesson.getCapacity(), reservedCount, lesson.getStatus(),
                lesson.getPublishAt(), lesson.getCanceledAt(), lesson.getCancelReason(), lesson.getCreatedAt());
    }
}
