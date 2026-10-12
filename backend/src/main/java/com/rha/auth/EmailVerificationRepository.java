package com.rha.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findFirstByEmailOrderByIdDesc(String email);

    Optional<EmailVerification> findByVerificationTokenHash(String verificationTokenHash);

    @Modifying
    @Query("DELETE FROM EmailVerification v WHERE v.email = :email")
    void deleteAllByEmail(@Param("email") String email);
}
