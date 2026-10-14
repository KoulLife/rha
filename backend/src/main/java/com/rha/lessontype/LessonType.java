package com.rha.lessontype;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "lesson_types")
public class LessonType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected LessonType() {
    }

    public static LessonType create(String code, String name, int capacity, Instant now) {
        LessonType type = new LessonType();
        type.code = code;
        type.name = name;
        type.capacity = capacity;
        type.active = true;
        type.createdAt = now;
        type.updatedAt = now;
        return type;
    }

    /** null인 필드는 바꾸지 않는다. code는 변경할 수 없다. */
    public void update(String name, Integer capacity, Boolean active, Instant now) {
        if (name != null) {
            this.name = name;
        }
        if (capacity != null) {
            this.capacity = capacity;
        }
        if (active != null) {
            this.active = active;
        }
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
