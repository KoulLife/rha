package com.rha.common.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

/**
 * 이미 읽은 엔티티의 행을 잠그고 최신 값으로 다시 읽는다.
 *
 * <p>잠금 쿼리({@code findByIdForUpdate})는 같은 트랜잭션에서 이미 읽은 엔티티를 다시 채우지 않아, 기다리는 동안
 * 다른 트랜잭션이 바꾼 값을 놓친다. 먼저 읽은 뒤에 잠가야 할 때는 이것으로 잠근다. 바꾸기 전에 잠가야 한다
 * (아직 반영하지 않은 변경은 버려진다).
 *
 * <p>교착을 피하려고 여러 행을 잠그는 작업은 룸 → 강사 → 부재 → 수업 → 예약·회원 → 이용권 상품 → 이용권 순으로
 * 잠그고, 같은 종류의 행 여러 개는 id 순으로 잠근다.
 */
@Component
public class RowLock {

    @PersistenceContext
    private EntityManager entityManager;

    public <T> T lock(T entity) {
        entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        return entity;
    }
}
