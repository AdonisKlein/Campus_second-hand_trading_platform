package com.campus.secondhand.user;

import com.campus.secondhand.common.EmailService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;

@Service
public class VerificationService {
    private static final int MAX_ATTEMPTS = 5;
    private final EmailVerificationRepository repository;
    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();
    private final byte[] pepper;
    private final TransactionTemplate transactions;

    public VerificationService(EmailVerificationRepository repository, EmailService emailService,
                               @Value("${app.verification.pepper}") String pepper,
                               TransactionTemplate transactions) {
        this.repository = repository;
        this.emailService = emailService;
        if (pepper.length() < 32) throw new IllegalStateException("VERIFICATION_PEPPER 至少需要 32 个字符");
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
        this.transactions = transactions;
    }

    public void sendCode(String email, VerificationPurpose purpose) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        try {
            transactions.executeWithoutResult(status -> {
                LocalDateTime now = LocalDateTime.now();
                EmailVerification challenge = repository.findByEmailAndPurpose(email, purpose).map(latest -> {
                    if (latest.getCreatedAt().plusSeconds(60).isAfter(now)) {
                        throw new VerificationRateLimitException("请稍后再获取验证码");
                    }
                    return latest;
                }).orElseGet(EmailVerification::new);
                challenge.setEmail(email);
                challenge.setPurpose(purpose);
                challenge.setCodeHash(hash(email, purpose, code));
                challenge.setCreatedAt(now);
                challenge.setExpiresAt(now.plusMinutes(10));
                challenge.setAttempts(0);
                challenge.setUsed(false);
                repository.saveAndFlush(challenge);
            });
        } catch (DataIntegrityViolationException ex) {
            throw new VerificationRateLimitException("请稍后再获取验证码");
        }
        try {
            emailService.sendVerificationCode(email, code);
        } catch (RuntimeException ex) {
            transactions.executeWithoutResult(status -> repository.findByEmailAndPurpose(email, purpose).ifPresent(saved -> {
                saved.setUsed(true);
                repository.save(saved);
            }));
            throw ex;
        }
    }

    @Transactional
    public boolean verifyCode(String email, VerificationPurpose purpose, String code) {
        var optional = repository.findByEmailAndPurpose(email, purpose);
        if (optional.isEmpty()) return false;
        EmailVerification challenge = optional.get();
        if (challenge.isUsed() || challenge.getExpiresAt().isBefore(LocalDateTime.now())
            || challenge.getAttempts() >= MAX_ATTEMPTS) return false;
        boolean matches = MessageDigest.isEqual(
            challenge.getCodeHash().getBytes(StandardCharsets.US_ASCII),
            hash(email, purpose, code).getBytes(StandardCharsets.US_ASCII));
        if (!matches) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            if (challenge.getAttempts() >= MAX_ATTEMPTS) challenge.setUsed(true);
            repository.save(challenge);
            return false;
        }
        challenge.setUsed(true);
        repository.save(challenge);
        return true;
    }

    String hash(String email, VerificationPurpose purpose, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((purpose + "\0" + email + "\0" + code)
                .getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("无法生成验证码摘要", ex);
        }
    }
}
