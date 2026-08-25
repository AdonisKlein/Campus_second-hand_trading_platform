package com.campus.secondhand.common;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class EmailServiceTests {

    @Test
    void sendsVerificationFromConfiguredDomainAddress() {
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailService service = enabledService(sender);

        service.sendVerificationCode("student@example.com", "123456");

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertEquals("no-reply@notify.example.edu.cn", message.getValue().getFrom());
        assertEquals("student@example.com", message.getValue().getTo()[0]);
        assertEquals("校园二手平台验证码", message.getValue().getSubject());
    }

    @Test
    void disabledMailFailsWithoutContactingSmtp() {
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailService service = new EmailService(sender, false, "", "", "");

        assertThrows(MailDeliveryException.class,
            () -> service.sendVerificationCode("student@example.com", "123456"));
        verify(sender, never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
    }

    @Test
    void invalidEnabledConfigurationFailsFast() {
        JavaMailSender sender = mock(JavaMailSender.class);
        assertThrows(IllegalStateException.class,
            () -> new EmailService(sender, true, "not-an-email", "sender@example.com", "secret"));
        assertThrows(IllegalStateException.class,
            () -> new EmailService(sender, true, "sender@example.com", "", "secret"));
        assertThrows(IllegalStateException.class,
            () -> new EmailService(sender, true, "sender@example.com", "sender@example.com", ""));
    }

    @Test
    void smtpFailureUsesStableApplicationError() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailAuthenticationException("provider detail"))
            .when(sender).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
        EmailService service = enabledService(sender);

        MailDeliveryException error = assertThrows(MailDeliveryException.class,
            () -> service.sendVerificationCode("student@example.com", "123456"));
        assertEquals("邮件服务暂时不可用", error.getMessage());
    }

    private static EmailService enabledService(JavaMailSender sender) {
        return new EmailService(sender, true, "no-reply@notify.example.edu.cn",
            "no-reply@notify.example.edu.cn", "test-smtp-secret");
    }
}
