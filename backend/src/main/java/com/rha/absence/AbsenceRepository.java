package com.rha.absence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AbsenceRepository extends JpaRepository<Absence, Long>, JpaSpecificationExecutor<Absence> {

    /** 승인 대기 또는 승인된 부재 중 [start, end)와 겹치는 것이 있는지. */
    @Query("""
            select count(a) > 0 from Absence a
            where a.instructorId = :instructorId
              and a.status in (com.rha.absence.AbsenceStatus.REQUESTED, com.rha.absence.AbsenceStatus.APPROVED)
              and a.startAt < :end and a.endAt > :start
            """)
    boolean existsActiveOverlap(@Param("instructorId") Long instructorId, @Param("start") Instant start,
            @Param("end") Instant end);

    /** 승인된 부재 중 [start, end)와 겹치는 것이 있는지. 수업 배정을 막는 기준이다. */
    @Query("""
            select count(a) > 0 from Absence a
            where a.instructorId = :instructorId and a.status = com.rha.absence.AbsenceStatus.APPROVED
              and a.startAt < :end and a.endAt > :start
            """)
    boolean existsApprovedOverlap(@Param("instructorId") Long instructorId, @Param("start") Instant start,
            @Param("end") Instant end);

    /** 퇴사 처리 때 취소할 부재: 승인 대기 전부와 아직 시작하지 않은 승인된 부재. */
    @Query("""
            select a from Absence a
            where a.instructorId = :instructorId
              and (a.status = com.rha.absence.AbsenceStatus.REQUESTED
                   or (a.status = com.rha.absence.AbsenceStatus.APPROVED and a.startAt > :now))
            """)
    List<Absence> findCancelableOnResignation(@Param("instructorId") Long instructorId,
            @Param("now") Instant now);
}
