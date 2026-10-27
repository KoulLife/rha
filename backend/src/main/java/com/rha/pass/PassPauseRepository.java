package com.rha.pass;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PassPauseRepository extends JpaRepository<PassPause, Long> {

    Page<PassPause> findByPassId(Long passId, Pageable pageable);

    List<PassPause> findByPassIdInAndStatusNot(Collection<Long> passIds, PassPauseStatus status);

    Optional<PassPause> findByIdAndPassId(Long id, Long passId);

    boolean existsByPassIdAndStatus(Long passId, PassPauseStatus status);

    List<PassPause> findByPassIdAndStatusIn(Long passId, Collection<PassPauseStatus> statuses);

    /** 취소되지 않은 정지 중 [start, end]와 겹치는 것이 있는지. */
    @Query("""
            select count(pp) > 0 from PassPause pp
            where pp.passId = :passId and pp.status <> com.rha.pass.PassPauseStatus.CANCELED
              and pp.startDate <= :end and pp.endDate >= :start
            """)
    boolean existsOverlap(@Param("passId") Long passId, @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    /** 시작일이 된 예정 정지를 진행 중으로 바꾼다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PassPause pp set pp.status = com.rha.pass.PassPauseStatus.ACTIVE
            where pp.status = com.rha.pass.PassPauseStatus.SCHEDULED
              and pp.startDate <= :today and pp.endDate >= :today
            """)
    int activate(@Param("today") LocalDate today);

    /** 종료일이 지난 정지를 끝난 것으로 바꾼다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PassPause pp set pp.status = com.rha.pass.PassPauseStatus.ENDED
            where pp.status in (com.rha.pass.PassPauseStatus.SCHEDULED, com.rha.pass.PassPauseStatus.ACTIVE)
              and pp.endDate < :today
            """)
    int end(@Param("today") LocalDate today);
}
