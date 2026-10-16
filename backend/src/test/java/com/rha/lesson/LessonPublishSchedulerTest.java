package com.rha.lesson;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class LessonPublishSchedulerTest {

    @Test
    void 자동_공개는_처리_시간과_관계없이_1분_간격으로_실행된다() throws Exception {
        Scheduled scheduled = LessonPublishScheduler.class.getMethod("publishDueLessons")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled.fixedRate()).isEqualTo(60_000);
        assertThat(scheduled.fixedDelay()).isEqualTo(-1);
    }
}
