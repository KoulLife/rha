package com.rha.settlement;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettlementRepository
        extends JpaRepository<Settlement, Long>, JpaSpecificationExecutor<Settlement> {

    /** 상태 변경·폐기가 동시에 들어와 서로의 상태 확인을 지나치지 못하도록 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Settlement s where s.id = :id")
    Optional<Settlement> findByIdForUpdate(@Param("id") Long id);

    /** 같은 강사의 정산 중 기간이 [start, end]와 겹치는 것이 있는지. */
    @Query("""
            select count(s) > 0 from Settlement s
            where s.instructorId = :instructorId and s.periodStart <= :end and s.periodEnd >= :start
            """)
    boolean existsOverlapping(@Param("instructorId") Long instructorId, @Param("start") LocalDate start,
            @Param("end") LocalDate end);
}
