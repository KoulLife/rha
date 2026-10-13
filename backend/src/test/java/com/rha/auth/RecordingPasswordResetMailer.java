package com.rha.auth;

import java.util.ArrayList;
import java.util.List;

/** 실제 메일을 보내지 않고 발송 내역만 기록한다. */
class RecordingPasswordResetMailer implements PasswordResetMailer {

    record Sent(String email, String token) {
    }

    private final List<Sent> sent = new ArrayList<>();

    @Override
    public void sendResetToken(String email, String token) {
        sent.add(new Sent(email, token));
    }

    List<Sent> sent() {
        return sent;
    }

    void clear() {
        sent.clear();
    }
}
