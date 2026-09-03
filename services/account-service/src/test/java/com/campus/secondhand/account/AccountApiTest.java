package com.campus.secondhand.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountApiTest {
    private static final String INTERNAL_TOKEN = "test-internal-token-012345678901234567";
    private static final String SECRET = "test-jwt-secret-012345678901234567890123";

    @Autowired
    private MockMvc mvc;

    @Autowired private UserRepository users;
    @Autowired private VerificationService verification;
    @Autowired private PasswordEncoder passwords;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM email_verification");
        jdbc.update("DELETE FROM account_event_outbox");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void protectedEndpointRejectsMissingGatewayJwt() throws Exception {
        mvc.perform(get("/api/users/me").header("X-Correlation-Id", "account-check-7"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-Id", "account-check-7"))
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(put("/api/users/me").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/admin/users/7/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalEndpointRejectsMissingServiceTokenWithJson() throws Exception {
        mvc.perform(post("/internal/auth/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student@example.com\",\"password\":\"abc123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void internalAuthenticationDoesNotRevealUnknownAccount() throws Exception {
        mvc.perform(post("/internal/auth/authenticate")
                        .header("X-Internal-Service-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@example.com\",\"password\":\"abc123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("邮箱或密码错误"));
    }

    @Test
    void internalPublicProfileRequiresServiceToken() throws Exception {
        mvc.perform(get("/internal/users/999/public"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(get("/internal/users/999/public")
                        .header("X-Internal-Service-Token", INTERNAL_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void disabledMailAdapterReturnsServiceUnavailable() throws Exception {
        mvc.perform(post("/api/auth/verification/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void verificationRegisterEndpointValidatesRequest() throws Exception {
        mvc.perform(post("/api/auth/verification/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verificationResetEndpointValidatesRequest() throws Exception {
        mvc.perform(post("/api/auth/verification/reset-password").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerCreatesAccountAndPublishesProfileEvent() throws Exception {
        verification.prepare("new@example.com", "REGISTER", "123456");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new-user\",\"nickname\":\"新同学\",\"email\":\"NEW@EXAMPLE.COM\",\"password\":\"abc123\",\"code\":\"123456\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("new-user"))
                .andExpect(jsonPath("$.data.email").value("new@example.com"));
        User user = users.findByEmailIgnoreCase("new@example.com").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(passwords.matches("abc123", user.getPasswordHash())).isTrue();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from account_event_outbox", Integer.class)).isEqualTo(1);
    }

    @Test
    void registerRejectsInvalidParameters() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"email\":\"bad\",\"password\":\"short\",\"code\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void resetPasswordUpdatesHashAndAuthVersion() throws Exception {
        User user = user("reset-user", "reset@example.com", "old123", "STUDENT");
        verification.prepare("reset@example.com", "RESET_PASSWORD", "654321");
        mvc.perform(post("/api/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"RESET@EXAMPLE.COM\",\"code\":\"654321\",\"newPassword\":\"next123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("密码重置成功"));
        User updated = users.findById(user.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(passwords.matches("next123", updated.getPasswordHash())).isTrue();
        org.assertj.core.api.Assertions.assertThat(updated.getAuthVersion()).isEqualTo(1);
    }

    @Test
    void resetPasswordRejectsBadCodeAndInvalidParameters() throws Exception {
        user("reset-user", "reset@example.com", "old123", "STUDENT");
        verification.prepare("reset@example.com", "RESET_PASSWORD", "654321");
        mvc.perform(post("/api/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"reset@example.com\",\"code\":\"000000\",\"newPassword\":\"next123\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(post("/api/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bad\",\"code\":\"\",\"newPassword\":\"bad\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownProfileCanBeReadAndUpdatedUsingJwtIdentity() throws Exception {
        User user = user("alice", "alice@example.com", "abc123", "STUDENT");
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + jwt(user.getId(), "STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.email").value("alice@example.com"));
        mvc.perform(put("/api/users/me").header("Authorization", "Bearer " + jwt(user.getId(), "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"新昵称\",\"phone\":\"13800138000\",\"campusRegion\":\"沙河校区\",\"userId\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.nickname").value("新昵称"))
                .andExpect(jsonPath("$.data.campusRegion").value("沙河校区"));
        User updated = users.findById(user.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.getNickname()).isEqualTo("新昵称");
        org.assertj.core.api.Assertions.assertThat(users.findById(999L)).isEmpty();
    }

    @Test
    void profileUpdateRejectsInvalidRegion() throws Exception {
        User user = user("alice", "alice@example.com", "abc123", "STUDENT");
        mvc.perform(put("/api/users/me").header("Authorization", "Bearer " + jwt(user.getId(), "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"campusRegion\":\"外部地址\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void adminCanListStudentsAndChangeStatus() throws Exception {
        User student = user("alice", "alice@example.com", "abc123", "STUDENT");
        User admin = user("admin", "admin@example.com", "abc123", "ADMIN");
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + jwt(admin.getId(), "ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].email").value("alice@example.com"));
        mvc.perform(put("/api/admin/users/" + student.getId() + "/status")
                        .header("Authorization", "Bearer " + jwt(admin.getId(), "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DISABLED"));
        org.assertj.core.api.Assertions.assertThat(users.findById(student.getId()).orElseThrow().getAuthVersion()).isEqualTo(1);
    }

    @Test
    void adminEndpointsRejectStudentInvalidStatusAdminTargetAndMissingUser() throws Exception {
        User student = user("alice", "alice@example.com", "abc123", "STUDENT");
        User admin = user("admin", "admin@example.com", "abc123", "ADMIN");
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + jwt(student.getId(), "STUDENT")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/users/" + student.getId() + "/status").header("Authorization", "Bearer " + jwt(admin.getId(), "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BOGUS\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/users/" + admin.getId() + "/status").header("Authorization", "Bearer " + jwt(admin.getId(), "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/users/999/status").header("Authorization", "Bearer " + jwt(admin.getId(), "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isNotFound());
    }

    private User user(String username, String email, String password, String role) {
        User user = new User(); user.setUsername(username); user.setEmail(email); user.setPasswordHash(passwords.encode(password)); user.setRole(role);
        return users.saveAndFlush(user);
    }

    private String jwt(long user, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("campus-gateway").subject(String.valueOf(user)).claim("role", role)
                .issuedAt(now).expiresAt(now.plusSeconds(60)).build();
        JwtEncoder encoder = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
