package com.rha.instructor;

import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InstructorAvailabilityRepository extends JpaRepository<InstructorAvailability, Long> {

    List<InstructorAvailability> findByInstructorId(Long instructorId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from InstructorAvailability a where a.instructorId = :instructorId")
    int deleteAllOf(@Param("instructorId") Long instructorId);

    /** 그 요일의 시간대 하나가 [start, end]를 모두 포함하는지. */
    @Query("""
            select count(a) > 0 from InstructorAvailability a
            where a.instructorId = :instructorId and a.dayOfWeek = :day
              and a.startTime <= :start and a.endTime >= :end
            """)
    boolean existsCovering(@Param("instructorId") Long instructorId, @Param("day") String day,
            @Param("start") LocalTime start, @Param("end") LocalTime end);
}
