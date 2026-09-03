package com.campus.secondhand.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"app.mail.enabled=true", "app.mail.from=no-reply@example.com"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountVerificationApiTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired EmailVerificationRepository codes;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JavaMailSender mailSender;

    @BeforeEach
    void clean() {
        codes.deleteAll();
        users.deleteAll();
    }

    @Test
    void registrationAndPasswordResetVerificationSendMailAndPersistSeparateChallenges() throws Exception {
        User user = new User();
        user.setUsername("registered");
        user.setEmail("registered@example.com");
        user.setPasswordHash("unused");
        users.saveAndFlush(user);

        mvc.perform(post("/api/auth/verification/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"NEW@EXAMPLE.COM\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true));
        mvc.perform(post("/api/auth/verification/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"REGISTERED@EXAMPLE.COM\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true));

        verify(mailSender, org.mockito.Mockito.times(2)).send(any(SimpleMailMessage.class));
        assertThat(challengeCount("new@example.com", "REGISTER", false)).isEqualTo(1);
        assertThat(challengeCount("registered@example.com", "RESET_PASSWORD", false)).isEqualTo(1);
    }

    @Test
    void invalidEmailAndMailFailureHaveStableErrorsWithoutLeavingUsableChallenge() throws Exception {
        mvc.perform(post("/api/auth/verification/register")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));

        doThrow(new IllegalStateException("smtp unavailable")).when(mailSender).send(any(SimpleMailMessage.class));
        mvc.perform(post("/api/auth/verification/register")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"failed@example.com\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.success").value(false));
        assertThat(challengeCount("failed@example.com", "REGISTER", true)).isEqualTo(1);
    }

    private int challengeCount(String email, String purpose, boolean used) {
        return jdbc.queryForObject("select count(*) from email_verification where email=? and purpose=? and used=?",
                Integer.class, email, purpose, used);
    }
}
