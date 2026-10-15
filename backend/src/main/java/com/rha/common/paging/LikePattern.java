package com.rha.common.paging;

import java.util.Locale;

/** LIKE 부분 일치 패턴을 만드는 도우미. */
public final class LikePattern {

    private LikePattern() {
    }

    /**
     * 앞뒤 공백을 지우고 소문자로 바꾼 부분 일치 패턴을 만든다. 비어 있으면 null이다.
     * LIKE 와일드카드는 이스케이프(`\`)한다.
     */
    public static String contains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
