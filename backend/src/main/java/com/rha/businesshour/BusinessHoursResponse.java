package com.rha.businesshour;

import java.util.List;

public record BusinessHoursResponse(List<Item> businessHours) {

    /** 시각은 센터 현지 시각 HH:mm이다. */
    public record Item(String dayOfWeek, boolean closed, String openTime, String closeTime) {

        static Item from(BusinessHour hour) {
            return new Item(hour.getDayOfWeek(), hour.isClosed(),
                    hour.getOpenTime() == null ? null : hour.getOpenTime().toString(),
                    hour.getCloseTime() == null ? null : hour.getCloseTime().toString());
        }
    }
}
