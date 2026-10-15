package com.rha.instructor;

import java.util.List;

public record AvailabilityResponse(InstructorSummary instructor, List<SlotResponse> slots) {

    public record SlotResponse(String dayOfWeek, String startTime, String endTime) {

        static SlotResponse from(InstructorAvailability slot) {
            return new SlotResponse(slot.getDayOfWeek(), slot.getStartTime().toString(),
                    slot.getEndTime().toString());
        }
    }
}
