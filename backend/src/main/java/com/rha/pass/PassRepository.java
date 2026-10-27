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

    /** 상품 변경을 반영할 이용권. 반영 중에 들어온 예약 차감이 덮어써지지 않도록 잠근다(id 순). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from Pass p
            where p.passProductId = :passProductId and p.status in :statuses
            order by p.id
            """)
    List<Pass> findByPassProductIdAndStatusInForUpdate(@Param("passProductId") Long passProductId,
            @Param("statuses") Collection<PassStatus> statuses);

    /** 정지 등록·해제처럼 만료일을 바꾸는 작업이 서로 겹치지 않도록 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pass p where p.id = :id")
    Optional<Pass> findByIdForUpdate(@Param("id") Long id);

    /**
     * 예약에 쓸 수 있는 이용권. 환불되지 않았고, 수업일이 시작일~만료일 안에 있고, 취소되지 않은 일시정지 기간에
     * 속하지 않으며, 잔여 횟수가 있는 것을 만료일이 이른 순(같으면 먼저 발급된 순)으로 반환한다.
     */
    @Query("""
            select p from Pass p
            where p.memberId = :memberId and p.remainingCount > 0
              and p.status <> com.rha.pass.PassStatus.REFUNDED
              and p.startDate <= :lessonDate and p.endDate >= :lessonDate
              and not exists (select 1 from PassPause pp
                              where pp.passId = p.id and pp.status <> com.rha.pass.PassPauseStatus.CANCELED
                                and pp.startDate <= :lessonDate and pp.endDate >= :lessonDate)
            order by p.endDate asc, p.id asc
            """)
    List<Pass> findReservableCandidates(@Param("memberId") Long memberId,
            @Param("lessonDate") LocalDate lessonDate);

    /** 잠근 이용권이 지금도 그 수업일에 예약할 수 있는지. 조건은 {@link #findReservableCandidates}와 같다. */
    @Query("""
            select count(p) > 0 from Pass p
            where p.id = :passId and p.remainingCount > 0
              and p.status <> com.rha.pass.PassStatus.REFUNDED
              and p.startDate <= :lessonDate and p.endDate >= :lessonDate
              and not exists (select 1 from PassPause pp
                              where pp.passId = p.id and pp.status <> com.rha.pass.PassPauseStatus.CANCELED
                                and pp.startDate <= :lessonDate and pp.endDate >= :lessonDate)
            """)
    boolean isReservableOn(@Param("passId") Long passId, @Param("lessonDate") LocalDate lessonDate);

    /**
     * 만료일이 지났지만 아직 EXPIRED가 아닌 이용권(환불된 이용권 제외). 이용권마다 활동 이력을 남기기 위해 한 건씩
     * 바꾸며, 동시에 상태를 갱신하는 요청이 같은 만료를 두 번 기록하지 않도록 행을 잠근다(id 순).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from Pass p
            where p.endDate < :today
              and p.status not in (com.rha.pass.PassStatus.EXPIRED, com.rha.pass.PassStatus.REFUNDED)
            order by p.id
            """)
    List<Pass> findExpiringForUpdate(@Param("today") LocalDate today);

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
