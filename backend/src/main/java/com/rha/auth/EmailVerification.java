package com.rha.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "email_verifications")
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String codeHash;

    @Column(nullable = false)
    private Instant codeExpiresAt;

    private Instant verifiedAt;

    private String verificationTokenHash;

    private Instant tokenExpiresAt;

    private Instant usedAt;

    @Column(nullable = false)
    private int failedAttempts;

    @Column(nullable = false)
    private Instant createdAt;

    protected EmailVerification() {
    }

    public EmailVerification(String email, String codeHash, Instant codeExpiresAt, Instant createdAt) {
        this.email = email;
        this.codeHash = codeHash;
        this.codeExpiresAt = codeExpiresAt;
        this.createdAt = createdAt;
    }

    /** 이 횟수만큼 틀리면 코드를 폐기한다. */
    public static final int MAX_FAILED_ATTEMPTS = 5;

    /** 아직 확인되지 않았고, 만료 전이며, 시도 횟수가 남아 있는 코드를 시도할 수 있다. */
    public boolean canAttempt(Instant now) {
        return verifiedAt == null
                && now.isBefore(codeExpiresAt)
                && failedAttempts < MAX_FAILED_ATTEMPTS;
    }

    public boolean matchesCode(String codeHash) {
        return TokenHasher.matches(this.codeHash, codeHash);
    }

    public void recordFailure() {
        failedAttempts++;
    }

    public void verify(String verificationTokenHash, Instant verifiedAt, Instant tokenExpiresAt) {
        this.verificationTokenHash = verificationTokenHash;
        this.verifiedAt = verifiedAt;
        this.tokenExpiresAt = tokenExpiresAt;
    }

    /** 요청한 이메일의 토큰이고, 아직 사용되지 않았으며, 만료 전인지 확인한다. */
    public boolean matchesToken(String email, Instant now) {
        return this.email.equals(email)
                && verificationTokenHash != null
                && usedAt == null
                && now.isBefore(tokenExpiresAt);
    }

    public void markUsed(Instant usedAt) {
        this.usedAt = usedAt;
    }
}
