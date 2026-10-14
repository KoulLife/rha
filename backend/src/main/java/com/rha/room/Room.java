package com.rha.room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int capacity;

    private Instant deletedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Room() {
    }

    public static Room create(String name, int capacity, Instant now) {
        Room room = new Room();
        room.name = name;
        room.capacity = capacity;
        room.createdAt = now;
        room.updatedAt = now;
        return room;
    }

    /** null인 필드는 바꾸지 않는다. */
    public void update(String name, Integer capacity, Instant now) {
        if (name != null) {
            this.name = name;
        }
        if (capacity != null) {
            this.capacity = capacity;
        }
        this.updatedAt = now;
    }

    /** 지난 수업이 참조하므로 행은 지우지 않고 삭제 시각만 기록한다. */
    public void delete(Instant now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getCapacity() {
        return capacity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
