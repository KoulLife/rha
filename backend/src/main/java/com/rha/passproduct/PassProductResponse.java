package com.rha.passproduct;

import java.time.Instant;

public record PassProductResponse(Long id, String name, long price, int totalCount, int validityDays,
        String description, Instant createdAt) {

    static PassProductResponse from(PassProduct product) {
        return new PassProductResponse(product.getId(), product.getName(), product.getPrice(),
                product.getTotalCount(), product.getValidityDays(), product.getDescription(),
                product.getCreatedAt());
    }
}
