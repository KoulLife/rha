package com.rha.activity;

import java.util.Objects;
import java.util.function.Function;

/** 변경 전후 값. 값은 문자열로 남기며 개인정보는 마스킹하고 비밀번호와 토큰은 기록하지 않는다. */
public record ActivityChange(String field, String before, String after) {

    public static ActivityChange of(String field, Object before, Object after) {
        return new ActivityChange(field, before == null ? null : before.toString(),
                after == null ? null : after.toString());
    }

    /** 값이 다를 때만 변경으로 본다. 같으면 null이다. */
    public static ActivityChange ifChanged(String field, Object before, Object after) {
        return Objects.equals(before, after) ? null : of(field, before, after);
    }

    /** 실제 값으로 비교하고, 이력에는 마스킹한 값을 남긴다. 마스킹하면 같아지는 변경도 놓치지 않는다. */
    public static <T> ActivityChange ifChanged(String field, T before, T after, Function<T, String> mask) {
        if (Objects.equals(before, after)) {
            return null;
        }
        return new ActivityChange(field, before == null ? null : mask.apply(before),
                after == null ? null : mask.apply(after));
    }
}
