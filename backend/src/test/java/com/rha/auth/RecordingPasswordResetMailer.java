package com.rha.auth;

import java.util.ArrayList;
import java.util.List;

/** 실제 메일을 보내지 않고 발송 내역만 기록한다. */
public class RecordingPasswordResetMailer implements PasswordResetMailer {

    public record Sent(String email, String token) {
    }

    private final List<Sent> sent = new ArrayList<>();

    @Override
    public void sendResetToken(String email, String token) {
        sent.add(new Sent(email, token));
    }

    public List<Sent> sent() {
        return sent;
    }

    /** 관리자 초대 메일 발송 내역. */
    private final List<Sent> invitations = new ArrayList<>();

    private boolean failOnSend;

    @Override
    public void sendInvitationToken(String email, String token) {
        if (failOnSend) {
            throw new IllegalStateException("메일 발송 실패(테스트)");
        }
        invitations.add(new Sent(email, token));
    }

    public List<Sent> invitations() {
        return invitations;
    }

    public void failOnSend(boolean failOnSend) {
        this.failOnSend = failOnSend;
    }

    public void clear() {
        sent.clear();
        invitations.clear();
        failOnSend = false;
    }
}
