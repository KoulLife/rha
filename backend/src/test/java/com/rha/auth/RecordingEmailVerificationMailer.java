package com.rha.auth;

import java.util.ArrayList;
import java.util.List;

/** 실제 메일을 보내지 않고 발송 내역만 기록한다. */
class RecordingEmailVerificationMailer implements EmailVerificationMailer {

    record Sent(String email, String code) {
    }

    private final List<Sent> sent = new ArrayList<>();

    @Override
    public void sendCode(String email, String code) {
        sent.add(new Sent(email, code));
    }

    List<Sent> sent() {
        return sent;
    }

    void clear() {
        sent.clear();
    }
}
