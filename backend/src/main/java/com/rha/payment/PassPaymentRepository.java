package com.rha.payment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PassPaymentRepository
        extends JpaRepository<PassPayment, Long>, JpaSpecificationExecutor<PassPayment> {

    Page<PassPayment> findByPassId(Long passId, Pageable pageable);

    Optional<PassPayment> findByIdAndPassId(Long id, Long passId);

    /** 이용권의 결제 합계. 환불로 체크된 기록도 포함한다. */
    @Query("select coalesce(sum(p.amount), 0) from PassPayment p where p.passId = :passId")
    long sumByPassId(@Param("passId") Long passId);

    /** 정정할 기록 자신을 뺀 이용권의 결제 합계. */
    @Query("""
            select coalesce(sum(p.amount), 0) from PassPayment p
            where p.passId = :passId and p.id <> :excludeId
            """)
    long sumByPassIdExcluding(@Param("passId") Long passId, @Param("excludeId") Long excludeId);

    /** 이용권별 결제 합계. 결제 기록이 없는 이용권은 결과에 없다. */
    @Query("""
            select p.passId, sum(p.amount) from PassPayment p
            where p.passId in :passIds group by p.passId
            """)
    List<Object[]> sumsByPassIds(@Param("passIds") Collection<Long> passIds);
}
