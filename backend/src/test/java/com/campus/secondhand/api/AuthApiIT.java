package com.campus.secondhand.api;

import com.campus.secondhand.user.User;
import com.campus.secondhand.user.EmailVerification;
import com.campus.secondhand.user.VerificationPurpose;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthApiIT extends AbstractApiIntegrationTest {
    @Autowired com.campus.secondhand.user.VerificationService verificationService;
    @Test
    void loginCreatesSessionAndLogoutRevokesIt() throws Exception {
        User student = createUser("auth-student", "auth-student@example.com", "STUDENT");
        MockCookie session = login(student.getEmail());
        mvc.perform(get("/users/me").cookie(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(student.getId()));
        mvc.perform(post("/auth/logout").cookie(session).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/users/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void loginFailureUsesSameMessageForUnknownAndWrongPassword() throws Exception {
        createUser("auth-known", "auth-known@example.com", "STUDENT");
        for (String email : new String[]{"auth-known@example.com", "missing@example.com"}) {
            mvc.perform(post("/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"%s\",\"password\":\"wrong123\"}".formatted(email)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("邮箱或密码错误"));
        }
    }

    @Test
    void authVersionChangeInvalidatesExistingSession() throws Exception {
        User student = createUser("auth-revoke", "auth-revoke@example.com", "STUDENT");
        MockCookie session = login(student.getEmail());
        student.setAuthVersion(student.getAuthVersion() + 1);
        users.saveAndFlush(student);
        mvc.perform(get("/users/me").cookie(session)).andExpect(status().isForbidden());
    }

    @Test
    void passwordResetApiConsumesResetCodeAndInvalidatesOldPassword() throws Exception {
        User student = createUser("auth-reset", "auth-reset@example.com", "STUDENT");
        String code = "654321";
        EmailVerification challenge = new EmailVerification();
        challenge.setEmail(student.getEmail());
        challenge.setPurpose(VerificationPurpose.RESET_PASSWORD);
        challenge.setCodeHash("placeholder");
        challenge.setCreatedAt(LocalDateTime.now().minusSeconds(61));
        challenge.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        verifications.saveAndFlush(challenge);
        try {
            var hash = com.campus.secondhand.user.VerificationService.class
                .getDeclaredMethod("hash", String.class, VerificationPurpose.class, String.class);
            hash.setAccessible(true);
            challenge.setCodeHash((String) hash.invoke(verificationService, student.getEmail(),
                VerificationPurpose.RESET_PASSWORD, code));
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("cannot prepare reset verification", ex);
        }
        verifications.saveAndFlush(challenge);

        mvc.perform(post("/auth/password/reset").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"code\":\"%s\",\"newPassword\":\"new456\"}"
                    .formatted(student.getEmail(), code)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").value("密码重置成功"));
        mvc.perform(post("/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"abc123\"}".formatted(student.getEmail())))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"new456\"}".formatted(student.getEmail())))
            .andExpect(status().isOk());
        mvc.perform(post("/auth/password/reset").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"code\":\"%s\",\"newPassword\":\"other456\"}"
                    .formatted(student.getEmail(), code)))
            .andExpect(status().isBadRequest());
    }
}
