package com.campus.secondhand.account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerificationService {
    private final EmailVerificationRepository codes;
    private final Clock clock;
    private final byte[] pepper;

    public VerificationService(EmailVerificationRepository codes, Clock clock,
            @Value("${app.verification.pepper}") String pepper) {
        this.codes = codes;
        this.clock = clock;
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public void prepare(String email, String purpose, String code) {
        EmailVerification challenge = codes.findByEmailAndPurpose(email, purpose)
                .orElseGet(EmailVerification::new);
        LocalDateTime now = LocalDateTime.now(clock);
        if (challenge.getCreatedAt() != null && challenge.getCreatedAt().plusSeconds(60).isAfter(now)) {
            throw new VerificationRateLimitException();
        }
        challenge.setEmail(email);
        challenge.setPurpose(purpose);
        challenge.setCodeHash(hash(email, purpose, code));
        challenge.setCreatedAt(now);
        challenge.setExpiresAt(now.plusMinutes(10));
        challenge.setAttempts(0);
        challenge.setUsed(false);
        codes.saveAndFlush(challenge);
    }

    @Transactional
    public boolean verify(String email, String purpose, String code) {
        var optional = codes.findByEmailAndPurpose(email, purpose);
        if (optional.isEmpty()) return false;
        var challenge = optional.get();
        LocalDateTime now = LocalDateTime.now(clock);
        if (challenge.isUsed() || challenge.getExpiresAt().isBefore(now) || challenge.getAttempts() >= 5) return false;
        boolean matches = MessageDigest.isEqual(
                challenge.getCodeHash().getBytes(StandardCharsets.US_ASCII),
                hash(email, purpose, code).getBytes(StandardCharsets.US_ASCII));
        if (!matches) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            if (challenge.getAttempts() >= 5) challenge.setUsed(true);
        } else {
            challenge.setUsed(true);
        }
        codes.save(challenge);
        return matches;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void invalidate(String email, String purpose) {
        codes.findByEmailAndPurpose(email, purpose).ifPresent(challenge -> {
            challenge.setUsed(true);
            codes.save(challenge);
        });
    }

    public String hash(String email, String purpose, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(
                    (purpose + "\0" + email.toLowerCase(Locale.ROOT) + "\0" + code)
                            .getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("无法生成验证码摘要", ex);
        }
    }
}
