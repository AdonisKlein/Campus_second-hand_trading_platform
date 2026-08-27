package com.campus.secondhand.account;

import java.time.Clock;
import java.time.LocalDateTime;
import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final UserRepository users;
    private final VerificationService verification;
    private final PasswordEncoder passwords;
    private final JavaMailSender mailSender;
    private final Clock clock;
    private final String mailFrom;
    private final boolean mailEnabled;
    private final SecureRandom random = new SecureRandom();

    public AccountService(UserRepository users, VerificationService verification,
            PasswordEncoder passwords, JavaMailSender mailSender, Clock clock,
            @Value("${app.mail.enabled:false}") boolean mailEnabled, @Value("${app.mail.from:}") String mailFrom) {
        this.users = users; this.verification = verification; this.passwords = passwords;
        this.mailSender = mailSender; this.clock = clock; this.mailEnabled = mailEnabled; this.mailFrom = mailFrom;
    }

    public void sendCode(String email, String purpose) {
        if (!mailEnabled) throw new MailUnavailableException();
        String code = String.format("%06d", random.nextInt(1_000_000));
        verification.prepare(email, purpose, code);
        try {
            SimpleMailMessage message = new SimpleMailMessage(); message.setFrom(mailFrom); message.setTo(email);
            message.setSubject("校园二手平台验证码"); message.setText("你的验证码是：" + code + "，10分钟内有效。"); mailSender.send(message);
        } catch (RuntimeException ex) {
            verification.invalidate(email, purpose); throw new MailUnavailableException();
        }
    }

    @Transactional(noRollbackFor = InvalidVerificationCodeException.class)
    public User register(String email, String username, String nickname, String password, String code) {
        if (!verification.verify(email, "REGISTER", code)) throw new InvalidVerificationCodeException();
        if (users.existsByEmail(email) || users.existsByUsername(username.trim())) throw new DataIntegrityViolationException("duplicate");
        User user = new User(); user.setEmail(email); user.setUsername(username.trim()); user.setNickname(nickname);
        user.setPasswordHash(passwords.encode(password)); return users.saveAndFlush(user);
    }

    @Transactional(noRollbackFor = InvalidVerificationCodeException.class)
    public User reset(String email, String code, String password) {
        if (!verification.verify(email, "RESET_PASSWORD", code)) throw new InvalidVerificationCodeException();
        User user = users.findByEmailIgnoreCase(email).orElseThrow(); user.setPasswordHash(passwords.encode(password));
        user.setAuthVersion(user.getAuthVersion() + 1); return users.save(user);
    }

    @Transactional
    public User authenticate(String email, String password) {
        User user = users.findByEmailIgnoreCase(email).orElseThrow(InvalidCredentialsException::new);
        if (!"ACTIVE".equals(user.getStatus()) || !passwords.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        user.setLastActiveAt(LocalDateTime.now(clock)); return users.save(user);
    }
}
