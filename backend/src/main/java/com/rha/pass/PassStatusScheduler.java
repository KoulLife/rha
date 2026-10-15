package com.rha.pass;

import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 날짜가 바뀌면 이용권과 일시정지 상태를 전환한다. 테스트에서는 꺼 둔다. */
@Component
@ConditionalOnProperty(name = "rha.pass.status-refresh.enabled", havingValue = "true", matchIfMissing = true)
public class PassStatusScheduler {

    private final PassService passService;

    public PassStatusScheduler(PassService passService) {
        this.passService = passService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void refresh() {
        passService.refreshStatuses(LocalDate.now(ZoneId.of("Asia/Seoul")));
    }
}
