package com.rha.passproduct;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PassProductRepository
        extends JpaRepository<PassProduct, Long>, JpaSpecificationExecutor<PassProduct> {
}
