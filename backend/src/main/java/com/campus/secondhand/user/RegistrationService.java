package com.campus.secondhand.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
    private final UserRepository users;
    private final VerificationService verification;
    private final PasswordEncoder passwords;

    public RegistrationService(UserRepository users, VerificationService verification, PasswordEncoder passwords) {
        this.users = users;
        this.verification = verification;
        this.passwords = passwords;
    }

    @Transactional
    public User register(String email, String username, String nickname, String password, String code) {
        if (!verification.verifyCode(email, VerificationPurpose.REGISTER, code)) {
            throw new InvalidVerificationCodeException();
        }
        User user = new User();
        user.setEmail(email);
        user.setUsername(username.trim());
        user.setNickname(nickname);
        user.setPasswordHash(passwords.encode(password));
        return users.saveAndFlush(user);
    }
}
