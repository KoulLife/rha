package com.rha.lesson;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LessonRepository extends JpaRepository<Lesson, Long>, JpaSpecificationExecutor<Lesson> {

    /** 예약의 정원 확인과 저장, 수업 변경과 취소가 서로 끼어들지 못하도록 수업 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lesson l where l.id = :id")
    Optional<Lesson> findByIdForUpdate(@Param("id") Long id);

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

    /** 강사가 담당하는 수업 중 {@code from} 이후에 시작하고 취소되지 않은 것을 잠근다(id 순). 수업 인계 때 바꾼다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l from Lesson l
            where l.instructorId = :instructorId and l.status <> com.rha.lesson.LessonStatus.CANCELED
            and l.startAt > :from
            order by l.id
            """)
    java.util.List<Lesson> findUpcomingOfForUpdate(@Param("instructorId") Long instructorId,
            @Param("from") Instant from);

    /**
     * 강사의 수업 중 [start, end)와 겹치고 아직 시작하지 않았으며 취소되지 않은 것. 부재 승인 때 취소하므로,
     * 확인과 취소 사이에 예약이 끼어들지 못하도록 잠근다(id 순).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l from Lesson l
            where l.instructorId = :instructorId and l.status <> com.rha.lesson.LessonStatus.CANCELED
              and l.startAt < :end and l.endAt > :start and l.startAt > :now
            order by l.id
            """)
    java.util.List<Lesson> findUpcomingOverlappingForUpdate(@Param("instructorId") Long instructorId,
            @Param("start") Instant start, @Param("end") Instant end, @Param("now") Instant now);

    /**
     * 정산 대상 수업: 시작 시각이 [rangeStart, rangeEnd)에 있고, 취소되지 않았으며, 이미 끝났고,
     * 다른 정산에 포함되지 않은 담당 수업. 계산하는 동안 출결이 바뀌지 않도록 잠근다(id 순).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l from Lesson l
            where l.instructorId = :instructorId and l.status <> com.rha.lesson.LessonStatus.CANCELED
              and l.startAt >= :rangeStart and l.startAt < :rangeEnd and l.endAt <= :now
              and not exists (select 1 from SettlementItem i where i.lessonId = l.id)
            order by l.id
            """)
    java.util.List<Lesson> findLessonsToSettleForUpdate(@Param("instructorId") Long instructorId,
            @Param("rangeStart") Instant rangeStart, @Param("rangeEnd") Instant rangeEnd,
            @Param("now") Instant now);

    /**
     * 공개 예약 시각이 지나 공개해야 하는 수업. 수업마다 활동 이력을 남기기 위해 한 건씩 바꾸며, 그 사이 취소된 수업을
     * 덮어쓰지 않도록 잠근다(id 순). 다른 트랜잭션이 잠근 수업은 그 트랜잭션이 끝난 뒤의 상태로 다시 걸러진다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l from Lesson l
            where l.status = com.rha.lesson.LessonStatus.SCHEDULED and l.publishAt <= :now
            order by l.id
            """)
    List<Lesson> findDueForPublishForUpdate(@Param("now") Instant now);
}
