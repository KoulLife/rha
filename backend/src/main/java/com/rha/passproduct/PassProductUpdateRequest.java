package com.rha.passproduct;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 전달한 필드만 수정한다. {@code description}은 null을 보내면 삭제를 뜻하므로
 * 전달 여부를 따로 기록한다. {@code applyToIssued}만으로는 수정할 필드가 있다고 보지 않는다.
 */
public class PassProductUpdateRequest {

    @Size(min = 1, max = 100)
    private String name;

    @Min(0)
    private Long price;

    @Min(1)
    @Max(1000)
    private Integer totalCount;

    @Min(1)
    @Max(3650)
    private Integer validityDays;

    @Size(max = 500)
    private String description;

    @JsonIgnore
    private boolean descriptionSpecified;

    private Boolean applyToIssued;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getPrice() {
        return price;
    }

    public void setPrice(Long price) {
        this.price = price;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public Integer getValidityDays() {
        return validityDays;
    }

    public void setValidityDays(Integer validityDays) {
        this.validityDays = validityDays;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.descriptionSpecified = true;
    }

    public boolean isDescriptionSpecified() {
        return descriptionSpecified;
    }

    public Boolean getApplyToIssued() {
        return applyToIssued;
    }

    public void setApplyToIssued(Boolean applyToIssued) {
        this.applyToIssued = applyToIssued;
    }

    public boolean hasAnyField() {
        return name != null || price != null || totalCount != null || validityDays != null
                || descriptionSpecified;
    }
}
