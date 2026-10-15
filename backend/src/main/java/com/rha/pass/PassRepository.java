package com.rha.pass;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PassRepository extends JpaRepository<Pass, Long>, JpaSpecificationExecutor<Pass> {

    boolean existsByPassProductId(Long passProductId);

    List<Pass> findByPassProductIdAndStatusIn(Long passProductId, Collection<PassStatus> statuses);

    /** 정지 등록·해제처럼 만료일을 바꾸는 작업이 서로 겹치지 않도록 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pass p where p.id = :id")
    Optional<Pass> findByIdForUpdate(@Param("id") Long id);

    /** 만료일이 지난 이용권을 EXPIRED로 바꾼다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Pass p set p.status = com.rha.pass.PassStatus.EXPIRED, p.updatedAt = :now
            where p.endDate < :today and p.status <> com.rha.pass.PassStatus.EXPIRED
            """)
    int expire(@Param("today") LocalDate today, @Param("now") Instant now);

    /** 진행 중인 정지가 있는 이용권을 PAUSED로 바꾼다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Pass p set p.status = com.rha.pass.PassStatus.PAUSED, p.updatedAt = :now
            where p.status in (com.rha.pass.PassStatus.UPCOMING, com.rha.pass.PassStatus.ACTIVE)
              and p.endDate >= :today
              and exists (select 1 from PassPause pp
                          where pp.passId = p.id and pp.status = com.rha.pass.PassPauseStatus.ACTIVE)
            """)
    int pause(@Param("today") LocalDate today, @Param("now") Instant now);

    /** 시작일이 되었고 진행 중인 정지가 없는 이용권을 ACTIVE로 바꾼다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Pass p set p.status = com.rha.pass.PassStatus.ACTIVE, p.updatedAt = :now
            where p.status in (com.rha.pass.PassStatus.UPCOMING, com.rha.pass.PassStatus.PAUSED)
              and p.startDate <= :today and p.endDate >= :today
              and not exists (select 1 from PassPause pp
                              where pp.passId = p.id and pp.status = com.rha.pass.PassPauseStatus.ACTIVE)
            """)
    int activate(@Param("today") LocalDate today, @Param("now") Instant now);
}
