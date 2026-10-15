package com.rha.reservation;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository
        extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {

    /** 취소되지 않은 예약 수. 정원 계산과 수업의 reservedCount에 쓴다. */
    long countByLessonIdAndStatusNot(Long lessonId, ReservationStatus status);

    boolean existsByLessonIdAndMemberIdAndStatusNot(Long lessonId, Long memberId, ReservationStatus status);

    boolean existsByLessonIdAndStatus(Long lessonId, ReservationStatus status);

    List<Reservation> findByLessonIdAndStatus(Long lessonId, ReservationStatus status);

    /** 수업별 취소되지 않은 예약 수. 예약이 없는 수업은 결과에 없다. */
    @Query("""
            select r.lessonId, count(r) from Reservation r
            where r.lessonId in :lessonIds and r.status <> com.rha.reservation.ReservationStatus.CANCELED
            group by r.lessonId
            """)
    List<Object[]> countsByLessonIds(@Param("lessonIds") Collection<Long> lessonIds);

    /** 수업·상태별 예약 수. 정산 계산에 쓴다. */
    @Query("""
            select r.lessonId, r.status, count(r) from Reservation r
            where r.lessonId in :lessonIds group by r.lessonId, r.status
            """)
    List<Object[]> countsByLessonIdsAndStatus(@Param("lessonIds") Collection<Long> lessonIds);

    /** 주어진 수업 중 예약(RESERVED)이 있는 수업의 id. */
    @Query("""
            select distinct r.lessonId from Reservation r
            where r.lessonId in :lessonIds and r.status = com.rha.reservation.ReservationStatus.RESERVED
            """)
    List<Long> lessonIdsWithReserved(@Param("lessonIds") Collection<Long> lessonIds);

    /** 회원에게 시작 전 예약(RESERVED)이 있는지. */
    @Query("""
            select count(r) > 0 from Reservation r, com.rha.lesson.Lesson l
            where r.lessonId = l.id and r.memberId = :memberId
              and r.status = com.rha.reservation.ReservationStatus.RESERVED and l.startAt > :now
            """)
    boolean existsUpcomingOf(@Param("memberId") Long memberId, @Param("now") Instant now);

    /** 이용권으로 한 예약(RESERVED) 중 수업 시작이 [from, to)에 속하는 것이 있는지. */
    @Query("""
            select count(r) > 0 from Reservation r, com.rha.lesson.Lesson l
            where r.lessonId = l.id and r.passId = :passId
              and r.status = com.rha.reservation.ReservationStatus.RESERVED
              and l.startAt >= :from and l.startAt < :to
            """)
    boolean existsReservedOnPassBetween(@Param("passId") Long passId, @Param("from") Instant from,
            @Param("to") Instant to);
}
