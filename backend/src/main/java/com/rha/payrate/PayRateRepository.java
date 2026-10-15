package com.rha.payrate;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayRateRepository extends JpaRepository<PayRate, PayRate.Key> {

    List<PayRate> findByInstructorIdOrderByLessonTypeIdAsc(Long instructorId);

    /** 강사의 단가를 모두 지운다. 목록으로 통째로 교체할 때 쓴다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PayRate r where r.instructorId = :instructorId")
    int deleteAllOf(@Param("instructorId") Long instructorId);
}
