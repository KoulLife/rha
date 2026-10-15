package com.rha.settlement;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettlementRepository
        extends JpaRepository<Settlement, Long>, JpaSpecificationExecutor<Settlement> {

    /** 같은 강사의 정산 중 기간이 [start, end]와 겹치는 것이 있는지. */
    @Query("""
            select count(s) > 0 from Settlement s
            where s.instructorId = :instructorId and s.periodStart <= :end and s.periodEnd >= :start
            """)
    boolean existsOverlapping(@Param("instructorId") Long instructorId, @Param("start") LocalDate start,
            @Param("end") LocalDate end);
}
