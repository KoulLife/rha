package com.rha.room;

import java.time.Instant;

public record RoomResponse(Long id, String name, int capacity, Instant createdAt) {

    public static RoomResponse from(Room room) {
        return new RoomResponse(room.getId(), room.getName(), room.getCapacity(), room.getCreatedAt());
    }
}
