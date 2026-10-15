package com.rha.settlement;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettlementItemRepository extends JpaRepository<SettlementItem, Long> {

    @Query("select count(i) > 0 from SettlementItem i where i.lessonId = :lessonId")
    boolean existsByLessonId(@Param("lessonId") Long lessonId);

    List<SettlementItem> findBySettlementId(Long settlementId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SettlementItem i where i.settlementId = :settlementId")
    int deleteAllOf(@Param("settlementId") Long settlementId);
}
