package com.campus.secondhand.governance;

import static org.mockito.ArgumentMatchers.*;import static org.mockito.Mockito.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.*;import java.util.*;import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;import org.springframework.http.MediaType;import org.springframework.security.oauth2.jose.jws.MacAlgorithm;import org.springframework.security.oauth2.jwt.*;import org.springframework.test.context.ActiveProfiles;import org.springframework.test.context.bean.override.mockito.MockitoBean;import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class GovernanceApiTest {
    private static final String SECRET="test-internal-jwt-secret-at-least-32-bytes";@Autowired MockMvc mvc;@MockitoBean ContentGovernance governance;
    @Test void protectedRoutesRejectAnonymous()throws Exception{mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());mvc.perform(get("/api/reports/mine")).andExpect(status().isUnauthorized());mvc.perform(get("/api/admin/reports")).andExpect(status().isUnauthorized());mvc.perform(put("/api/admin/reports/100").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());}
    @Test void reporterIdentityComesOnlyFromGatewayJwt()throws Exception{when(governance.submit(any(),any())).thenReturn(view());mvc.perform(post("/api/reports").header("Authorization","Bearer "+jwt(2,"STUDENT")).contentType(MediaType.APPLICATION_JSON).content("{\"targetType\":\"ITEM\",\"targetId\":10,\"reasonCode\":\"FRAUD\",\"description\":\"这是足够详细的举报说明文字\",\"reporterId\":999}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.reporterId").value(2));verify(governance).submit(argThat(actor->actor.userId()==2),any());}
    @Test void studentCannotUseAdminQueue()throws Exception{mvc.perform(get("/api/admin/reports").header("Authorization","Bearer "+jwt(2,"STUDENT"))).andExpect(status().isForbidden());}
    @Test void internalResultRequiresServiceToken()throws Exception{when(governance.applyActionResult(any())).thenReturn(view());String body="{\"eventId\":\"r1\",\"correlationId\":\"c1\",\"type\":\"GovernanceActionApplied\",\"reportId\":100}";mvc.perform(post("/internal/governance/action-results").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());mvc.perform(post("/internal/governance/action-results").header("X-Internal-Service-Token","test-internal-service-token-32-bytes").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());}
    @Test void studentCanListOwnReportsButNotAdminAndValidationIsExplicit()throws Exception{
        when(governance.listMine(any(),eq(0),eq(20))).thenReturn(new ContentGovernance.ReportPage(List.of(view()),0,20,false));
        String auth="Bearer "+jwt(2,"STUDENT");
        mvc.perform(get("/api/reports/mine").header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.reports[0].reporterId").value(2));
        mvc.perform(get("/api/admin/reports").header("Authorization",auth)).andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(post("/api/reports").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content("{\"targetType\":\"ITEM\",\"targetId\":0,\"reasonCode\":\"FRAUD\",\"description\":\"短\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        verify(governance).listMine(argThat(a->a.userId()==2),eq(0),eq(20));
    }
    @Test void adminQueueAndDecisionReturnResolvedReportAndRejectInvalidDecision()throws Exception{
        var resolved=new ContentGovernance.ReportView(100L,2L,"学生",ReportTargetType.ITEM,10L,7L,"教材",ReportReason.FRAUD,"这是足够详细的举报说明文字",ReportStatus.RESOLVED,GovernanceAction.REMOVE_ITEM,ActionState.PENDING,null,"已核查",LocalDateTime.now(),null,List.of());
        when(governance.listForAdmin(any(),eq(ReportStatus.OPEN),eq(0),eq(30))).thenReturn(new ContentGovernance.ReportPage(List.of(view()),0,30,false)); when(governance.decide(any(),eq(100L),any())).thenReturn(resolved);
        String auth="Bearer "+jwt(9,"ADMIN");
        mvc.perform(get("/api/admin/reports").header("Authorization",auth).queryParam("status","OPEN")).andExpect(status().isOk()).andExpect(jsonPath("$.data.reports[0].status").value("OPEN"));
        mvc.perform(put("/api/admin/reports/100").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\",\"action\":\"REMOVE_ITEM\",\"note\":\"确认违规并下架\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RESOLVED")).andExpect(jsonPath("$.data.actionState").value("PENDING"));
        mvc.perform(put("/api/admin/reports/100").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\",\"note\":\"\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        verify(governance).listForAdmin(argThat(a->a.userId()==9),eq(ReportStatus.OPEN),eq(0),eq(30)); verify(governance).decide(argThat(a->a.userId()==9),eq(100L),argThat(d->d.status()==ReportStatus.RESOLVED&&d.action()==GovernanceAction.REMOVE_ITEM));
    }
    private ContentGovernance.ReportView view(){return new ContentGovernance.ReportView(100L,2L,"学生",ReportTargetType.ITEM,10L,7L,"教材",ReportReason.FRAUD,"这是足够详细的举报说明文字",ReportStatus.OPEN,GovernanceAction.NONE,ActionState.NONE,null,null,LocalDateTime.now(),null,List.of());}
    private String jwt(long user,String role){Instant now=Instant.now();JwtClaimsSet claims=JwtClaimsSet.builder().issuer("campus-gateway").subject(String.valueOf(user)).claim("role",role).issuedAt(now).expiresAt(now.plusSeconds(60)).build();JwtEncoder encoder=NimbusJwtEncoder.withSecretKey(new SecretKeySpec(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256")).build();return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();}
}
