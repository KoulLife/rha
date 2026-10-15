package com.rha.lesson;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LessonRepository extends JpaRepository<Lesson, Long>, JpaSpecificationExecutor<Lesson> {

    /** 시작 전이고 취소되지 않은 수업이 룸에 있는지. */
    boolean existsByRoomIdAndStatusNotAndStartAtAfter(Long roomId, LessonStatus status, Instant now);

    /** 시작 전이고 취소되지 않은 담당 수업이 강사에게 있는지. */
    boolean existsByInstructorIdAndStatusNotAndStartAtAfter(Long instructorId, LessonStatus status, Instant now);

    /** 취소되지 않은 담당 수업 중 [start, end)와 겹치는 것이 있는지. 수정 중인 수업은 excludeId로 제외한다. */
    @Query("""
            select count(l) > 0 from Lesson l
            where l.instructorId = :instructorId and l.status <> com.rha.lesson.LessonStatus.CANCELED
              and l.startAt < :end and l.endAt > :start and l.id <> :excludeId
            """)
    boolean existsInstructorConflict(@Param("instructorId") Long instructorId, @Param("start") Instant start,
            @Param("end") Instant end, @Param("excludeId") Long excludeId);

    @Query("""
            select count(l) > 0 from Lesson l
            where l.roomId = :roomId and l.status <> com.rha.lesson.LessonStatus.CANCELED
              and l.startAt < :end and l.endAt > :start and l.id <> :excludeId
            """)
    boolean existsRoomConflict(@Param("roomId") Long roomId, @Param("start") Instant start,
            @Param("end") Instant end, @Param("excludeId") Long excludeId);

    /** 공개 예약 시각이 지난 수업을 공개한다. 바뀐 수업 수를 반환한다. */
    @Modifying(clearAutomatically = true)
    @Query("""
            update Lesson l set l.status = com.rha.lesson.LessonStatus.PUBLISHED, l.publishAt = null,
                   l.updatedAt = :now
            where l.status = com.rha.lesson.LessonStatus.SCHEDULED and l.publishAt <= :now
            """)
    int publishDue(@Param("now") Instant now);
}
