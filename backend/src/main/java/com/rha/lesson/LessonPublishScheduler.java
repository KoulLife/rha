package com.rha.lesson;

import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 공개 예약(SCHEDULED)된 수업을 예약 시각이 되면 PUBLISHED로 바꾼다. 테스트에서는 꺼 둔다. */
@Component
@ConditionalOnProperty(name = "rha.lesson.auto-publish.enabled", havingValue = "true", matchIfMissing = true)
public class LessonPublishScheduler {

    private final LessonService lessonService;

    public LessonPublishScheduler(LessonService lessonService) {
        this.lessonService = lessonService;
    }

    @Scheduled(fixedRate = 60_000, initialDelay = 60_000)
    public void publishDueLessons() {
        lessonService.publishDueLessons(Instant.now());
    }
}
