package com.rha.member;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 내 정보 부분 수정 요청. 전달한 필드만 수정한다.
 *
 * <p>`address`는 null이 "주소 삭제"를 뜻하므로, 필드를 보내지 않은 것과 null을 보낸 것을 구분한다.
 * 그 외 필드의 null은 전달하지 않은 것으로 본다. 정의되지 않은 필드(email, role 등)는 무시된다.
 */
public class MemberUpdateRequest {

    @Size(min = 1, max = 50)
    @Pattern(regexp = "(?s).*\\S.*", message = "공백만으로 이루어질 수 없습니다.")
    private String name;

    @Pattern(regexp = "^01[0-9]{8,9}$", message = "하이픈 없는 휴대폰 번호여야 합니다.")
    private String phoneNumber;

    private LocalDate birthDate;

    @Size(max = 200)
    private String address;

    @JsonIgnore
    private boolean addressSpecified;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getAddress() {
        return address;
    }

    /** 호출되었다는 것 자체가 JSON에 `address`가 있었다는 뜻이다(null 포함). */
    public void setAddress(String address) {
        this.address = address;
        this.addressSpecified = true;
    }

    public boolean isAddressSpecified() {
        return addressSpecified;
    }

    /** 수정할 수 있는 필드가 하나라도 전달되었는지 확인한다. */
    public boolean hasAnyField() {
        return name != null || phoneNumber != null || birthDate != null || addressSpecified;
    }
}
