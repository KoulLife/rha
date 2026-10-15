package com.rha.lesson;

import jakarta.validation.constraints.Size;

public record LessonCancelRequest(@Size(max = 200) String reason) {
}
