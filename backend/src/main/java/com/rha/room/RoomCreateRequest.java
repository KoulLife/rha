package com.rha.room;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record RoomCreateRequest(
        @NotNull @Pattern(regexp = "(?s)\\s*\\S(?:.{0,48}\\S)?\\s*", message = "공백을 제외하고 1~50자여야 합니다.") String name,
        @NotNull @Min(1) @Max(100) Integer capacity) {
}
