package com.campus.secondhand.user;

import com.campus.secondhand.common.EmailService;
import com.campus.secondhand.test.MutableTestClock;
import com.campus.secondhand.test.TestDataFactory;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerificationServiceTests {
    private static final String EMAIL = "student@example.com";
    private MutableTestClock clock;
    private EmailVerificationRepository repository;
    private EmailService email;
    private TransactionTemplate transactions;
    private VerificationService service;

    @BeforeEach
    void setUp() {
        clock = new MutableTestClock(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));
        repository = mock(EmailVerificationRepository.class);
        email = mock(EmailService.class);
        transactions = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());
        service = new VerificationService(repository, email, "test-pepper-that-is-at-least-32-chars", transactions, clock);
    }

    @Test
    void codeIsBoundToPurposeAndWrongPurposeCannotBeConsumed() {
        EmailVerification challenge = TestDataFactory.verification(EMAIL, VerificationPurpose.REGISTER,
            java.time.LocalDateTime.of(2026, 1, 1, 0, 10));
        challenge.setCodeHash(service.hash(EMAIL, VerificationPurpose.REGISTER, "123456"));
        when(repository.findByEmailAndPurpose(EMAIL, VerificationPurpose.REGISTER)).thenReturn(Optional.of(challenge));
        when(repository.findByEmailAndPurpose(EMAIL, VerificationPurpose.RESET_PASSWORD)).thenReturn(Optional.of(challenge));

        assertFalse(service.verifyCode(EMAIL, VerificationPurpose.RESET_PASSWORD, "123456"));
        assertFalse(challenge.isUsed());
        assertTrue(service.verifyCode(EMAIL, VerificationPurpose.REGISTER, "123456"));
        assertTrue(challenge.isUsed());
    }

    @Test
    void expiredCodeIsRejectedWithoutConsumingIt() {
        EmailVerification challenge = TestDataFactory.verification(EMAIL, VerificationPurpose.REGISTER,
            java.time.LocalDateTime.of(2026, 1, 1, 0, 10));
        challenge.setCodeHash(service.hash(EMAIL, VerificationPurpose.REGISTER, "123456"));
        when(repository.findByEmailAndPurpose(EMAIL, VerificationPurpose.REGISTER)).thenReturn(Optional.of(challenge));
        clock.advance(java.time.Duration.ofMinutes(11));

        assertFalse(service.verifyCode(EMAIL, VerificationPurpose.REGISTER, "123456"));
        assertFalse(challenge.isUsed());
        verify(repository, never()).save(any());
    }

    @Test
    void fiveWrongAttemptsDisableChallengeAndSixthAttemptCannotPass() {
        EmailVerification challenge = TestDataFactory.verification(EMAIL, VerificationPurpose.REGISTER,
            java.time.LocalDateTime.of(2026, 1, 1, 0, 10));
        challenge.setCodeHash(service.hash(EMAIL, VerificationPurpose.REGISTER, "123456"));
        when(repository.findByEmailAndPurpose(EMAIL, VerificationPurpose.REGISTER)).thenReturn(Optional.of(challenge));

        for (int i = 0; i < 5; i++) assertFalse(service.verifyCode(EMAIL, VerificationPurpose.REGISTER, "000000"));
        assertTrue(challenge.isUsed());
        assertFalse(service.verifyCode(EMAIL, VerificationPurpose.REGISTER, "123456"));
    }

    @Test
    void successfulCodeIsSingleUse() {
        EmailVerification challenge = TestDataFactory.verification(EMAIL, VerificationPurpose.REGISTER,
            java.time.LocalDateTime.of(2026, 1, 1, 0, 10));
        challenge.setCodeHash(service.hash(EMAIL, VerificationPurpose.REGISTER, "123456"));
        when(repository.findByEmailAndPurpose(EMAIL, VerificationPurpose.REGISTER)).thenReturn(Optional.of(challenge));

        assertTrue(service.verifyCode(EMAIL, VerificationPurpose.REGISTER, "123456"));
        assertFalse(service.verifyCode(EMAIL, VerificationPurpose.REGISTER, "123456"));
    }

    @Test
    void resendWithinSixtySecondsIsRateLimited() {
        when(repository.findByEmailAndPurpose(EMAIL, VerificationPurpose.REGISTER)).thenReturn(Optional.empty());
        service.sendCode(EMAIL, VerificationPurpose.REGISTER);
        EmailVerification saved = new EmailVerification();
        saved.setEmail(EMAIL);
        saved.setPurpose(VerificationPurpose.REGISTER);
        saved.setCreatedAt(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        when(repository.findByEmailAndPurpose(EMAIL, VerificationPurpose.REGISTER)).thenReturn(Optional.of(saved));

        assertThrows(VerificationRateLimitException.class,
            () -> service.sendCode(EMAIL, VerificationPurpose.REGISTER));
    }
}
