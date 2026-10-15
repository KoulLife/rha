package com.rha.lesson;

/** 상태와 시각은 문자열로 받아 서비스에서 해석한다. 권한 확인(403) 전에 형식 오류(400)가 나지 않게 하기 위함이다. */
public record LessonStatusChangeRequest(String status, String publishAt) {
}
