package com.campus.secondhand.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GovernanceApiPersistenceTest {
    private static final String SECRET = "test-internal-jwt-secret-at-least-32-bytes";

    @Autowired MockMvc mvc;
    @Autowired ContentReportRepository reports;
    @Autowired ReportActionRepository actions;
    @Autowired GovernanceOutboxRepository outbox;
    @MockitoBean HttpAccountGovernanceAdapter accounts;
    @MockitoBean HttpReportTargetAdapter targets;

    @BeforeEach
    void cleanDatabase() {
        actions.deleteAll();
        outbox.deleteAll();
        reports.deleteAll();
    }

    @Test
    void submitAndResolveReportPersistAuditAndOutboxThroughPublicApi() throws Exception {
        when(accounts.requireActiveStudent(2)).thenReturn(new AccountGovernancePort.AccountSnapshot(
                2, "student", "学生", "ACTIVE", "STUDENT"));
        when(accounts.requireActiveAdmin(9)).thenReturn(new AccountGovernancePort.AccountSnapshot(
                9, "admin", "管理员", "ACTIVE", "ADMIN"));
        when(targets.resolve(ReportTargetType.ITEM, 10)).thenReturn(new ReportTargetPort.TargetSnapshot(
                10, 7, "高等数学教材", ReportTargetType.ITEM, true));

        mvc.perform(post("/api/reports")
                        .header("Authorization", "Bearer " + jwt(2, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"ITEM\",\"targetId\":10,\"reasonCode\":\"FRAUD\","
                                + "\"description\":\"卖家要求脱离平台提前付款，疑似诈骗\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reporterId").value(2))
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        ContentReport report = reports.findAll().getFirst();
        assertThat(report.getReporterId()).isEqualTo(2);
        assertThat(report.getReportedUserId()).isEqualTo(7);
        assertThat(report.getTargetSummary()).isEqualTo("高等数学教材");
        assertThat(actions.count()).isZero();
        assertThat(outbox.count()).isZero();

        mvc.perform(put("/api/admin/reports/" + report.getId())
                        .header("Authorization", "Bearer " + jwt(9, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"action\":\"REMOVE_ITEM\","
                                + "\"note\":\"核查聊天记录后确认违规并下架\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.actionState").value("PENDING"));

        ContentReport resolved = reports.findById(report.getId()).orElseThrow();
        assertThat(resolved.getHandledBy()).isEqualTo(9);
        assertThat(resolved.getDecisionAction()).isEqualTo(GovernanceAction.REMOVE_ITEM);
        assertThat(resolved.getActionState()).isEqualTo(ActionState.PENDING);
        assertThat(actions.findAll()).singleElement().satisfies(action -> {
            assertThat(action.getReportId()).isEqualTo(report.getId());
            assertThat(action.getAdminId()).isEqualTo(9);
        });
        assertThat(outbox.findAll()).singleElement().satisfies(event -> {
            assertThat(event.payload()).contains("\"type\":\"GovernanceActionRequested\"")
                    .contains("\"reportId\":" + report.getId());
        });
    }

    private String jwt(long userId, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("campus-gateway").subject(String.valueOf(userId))
                .claim("role", role).issuedAt(now).expiresAt(now.plusSeconds(600)).build();
        JwtEncoder encoder = NimbusJwtEncoder.withSecretKey(
                new SecretKeySpec(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256")).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
