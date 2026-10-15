package com.rha.activity;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** 요약 문장에 쓰는 시각 표기. 센터 현지 시각(Asia/Seoul)으로 적는다. */
public final class ActivityTime {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("Asia/Seoul"));

    private ActivityTime() {
    }

    public static String format(Instant instant) {
        return FORMAT.format(instant);
    }

    public static String period(Instant start, Instant end) {
        return format(start) + " ~ " + format(end);
    }
}
