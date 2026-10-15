package com.rha.activity;

/** 활동 이력에 남기는 개인정보 마스킹. 비밀번호와 토큰은 아예 기록하지 않는다. */
public final class Masking {

    private Masking() {
    }

    /** 01012345678 → 010****5678 */
    public static String phone(String value) {
        if (value == null) {
            return null;
        }
        return value.length() < 8 ? "****" : value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    /** 1990-01-31 → 1990-**-** */
    public static String birthDate(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.length() < 4 ? "****" : text.substring(0, 4) + "-**-**";
    }

    /** 서울시 강남구 테헤란로 1 → 서울시 *** (첫 단어만 남긴다) */
    public static String address(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        int space = trimmed.indexOf(' ');
        return space < 0 ? "***" : trimmed.substring(0, space) + " ***";
    }

    /** a@example.com → a***@example.com */
    public static String email(String value) {
        if (value == null) {
            return null;
        }
        int at = value.indexOf('@');
        if (at < 1) {
            return "***";
        }
        return value.charAt(0) + "***" + value.substring(at);
    }
}
