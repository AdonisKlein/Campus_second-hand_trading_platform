package com.campus.secondhand.account;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailVerification> findByEmailAndPurpose(String email, String purpose);
}
