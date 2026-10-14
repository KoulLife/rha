package com.rha.room;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 전달한 필드만 수정한다. 최소 1개 필드가 필요하다. */
public record RoomUpdateRequest(
        @Size(min = 1, max = 50) @Pattern(regexp = "(?s).*\\S.*", message = "공백만으로 이루어질 수 없습니다.") String name,
        @Min(1) @Max(100) Integer capacity) {

    public boolean hasAnyField() {
        return name != null || capacity != null;
    }
}
