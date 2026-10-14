package com.rha.lessontype;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonTypeRepository extends JpaRepository<LessonType, Long> {

    boolean existsByCode(String code);

    Page<LessonType> findByActive(boolean active, Pageable pageable);

    List<LessonType> findByActiveTrueOrderByCodeAsc();
}
