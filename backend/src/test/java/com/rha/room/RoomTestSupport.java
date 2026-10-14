package com.rha.room;

import com.rha.ApiTestSupport;

abstract class RoomTestSupport extends ApiTestSupport {

    long insertRoom(String name, int capacity) {
        return jdbc.queryForObject("""
                INSERT INTO rooms (name, capacity, created_at, updated_at)
                VALUES (?, ?, now(), now())
                RETURNING id
                """, Long.class, name, capacity);
    }

    long insertDeletedRoom(String name, int capacity) {
        long id = insertRoom(name + "-tmp", capacity);
        jdbc.update("UPDATE rooms SET name = ?, deleted_at = now() WHERE id = ?", name, id);
        return id;
    }

    int roomCount() {
        return jdbc.queryForObject("SELECT count(*) FROM rooms WHERE deleted_at IS NULL", Integer.class);
    }
}
