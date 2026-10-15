package com.rha.room;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, Long> {

    Page<Room> findByDeletedAtIsNull(Pageable pageable);

    Optional<Room> findByIdAndDeletedAtIsNull(Long id);

    /** 수업 시간 충돌 검사·룸 삭제 확인과 저장 사이에 다른 수업이 끼어들지 못하도록 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id and r.deletedAt is null")
    Optional<Room> findActiveForUpdate(@Param("id") Long id);

    boolean existsByNameAndDeletedAtIsNull(String name);

    boolean existsByNameAndIdNotAndDeletedAtIsNull(String name, Long id);
}
