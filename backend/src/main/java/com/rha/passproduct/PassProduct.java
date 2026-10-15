package com.rha.passproduct;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "pass_products")
public class PassProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private long price;

    @Column(nullable = false)
    private int totalCount;

    @Column(nullable = false)
    private int validityDays;

    private String description;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected PassProduct() {
    }

    public static PassProduct create(String name, long price, int totalCount, int validityDays,
            String description, Instant now) {
        PassProduct product = new PassProduct();
        product.name = name;
        product.price = price;
        product.totalCount = totalCount;
        product.validityDays = validityDays;
        product.description = description;
        product.createdAt = now;
        product.updatedAt = now;
        return product;
    }

    /**
     * null인 필드는 바꾸지 않는다. 설명은 null이 삭제를 뜻하므로 {@code changeDescription}으로
     * 변경 여부를 따로 받는다.
     */
    public void update(String name, Long price, Integer totalCount, Integer validityDays,
            boolean changeDescription, String description, Instant now) {
        if (name != null) {
            this.name = name;
        }
        if (price != null) {
            this.price = price;
        }
        if (totalCount != null) {
            this.totalCount = totalCount;
        }
        if (validityDays != null) {
            this.validityDays = validityDays;
        }
        if (changeDescription) {
            this.description = description;
        }
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getPrice() {
        return price;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getValidityDays() {
        return validityDays;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
