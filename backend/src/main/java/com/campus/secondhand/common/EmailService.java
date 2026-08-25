package com.campus.secondhand.common;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String from;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.mail.enabled:false}") boolean enabled,
                        @Value("${app.mail.from:}") String from,
                        @Value("${spring.mail.username:}") String username,
                        @Value("${spring.mail.password:}") String password) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = from == null ? "" : from.trim();
        if (enabled) validateConfiguration(this.from, username, password);
    }

    public void sendVerificationCode(String to, String code) {
        if (!enabled) throw new MailDeliveryException("邮件服务尚未启用");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("校园二手平台验证码");
        message.setText("你的验证码是：" + code + "，10 分钟内有效。请勿转发给他人。");
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            throw new MailDeliveryException("邮件服务暂时不可用", ex);
        }
    }

    private static void validateConfiguration(String from, String username, String password) {
        if (from.isBlank()) throw new IllegalStateException("MAIL_ENABLED=true 时必须配置 MAIL_FROM");
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("MAIL_ENABLED=true 时必须配置 MAIL_USERNAME");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("MAIL_ENABLED=true 时必须配置 MAIL_PASSWORD");
        }
        try {
            InternetAddress address = new InternetAddress(from, true);
            if (address.getAddress() == null || !address.getAddress().contains("@")) throw new AddressException();
        } catch (AddressException ex) {
            throw new IllegalStateException("MAIL_FROM 必须是有效的发件邮箱地址", ex);
        }
    }
}
