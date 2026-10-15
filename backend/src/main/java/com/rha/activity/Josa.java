package com.rha.activity;

/** 요약 문장에 쓰는 한국어 조사 선택. 한글이 아닌 글자로 끝나면 "이(가)"처럼 병기한다. */
public final class Josa {

    private Josa() {
    }

    public static String iGa(String word) {
        return word + pick(word, "이", "가", "이(가)");
    }

    public static String eulReul(String word) {
        return word + pick(word, "을", "를", "을(를)");
    }

    public static String eunNeun(String word) {
        return word + pick(word, "은", "는", "은(는)");
    }

    private static String pick(String word, String withBatchim, String withoutBatchim, String unknown) {
        for (int i = word.length() - 1; i >= 0; i--) {
            char c = word.charAt(i);
            if (c >= 0xAC00 && c <= 0xD7A3) {
                return (c - 0xAC00) % 28 != 0 ? withBatchim : withoutBatchim;
            }
            if (Character.isLetterOrDigit(c)) {
                return unknown;
            }
        }
        return unknown;
    }
}
