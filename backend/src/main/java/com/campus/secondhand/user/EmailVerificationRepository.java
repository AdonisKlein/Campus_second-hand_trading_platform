package com.campus.secondhand.user;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
    List<EmailVerification> findByEmailOrderByCreatedAtDesc(String email);
    Optional<EmailVerification> findFirstByEmailOrderByCreatedAtDesc(String email);
}

