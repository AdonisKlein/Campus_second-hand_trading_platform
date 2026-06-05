package com.campus.secondhand.common;

import org.springframework.stereotype.Service;

@Service
public class EmailService {

    public EmailService() {
    }

    public void sendVerificationCode(String to, String code) {
        // In dev/test without SMTP, just log the code to console so testers can copy it.
        try {
            System.out.println("[EmailService] sendVerificationCode to=" + to + " code=" + code);
        } catch (Exception ignored) {
        }
    }
}
