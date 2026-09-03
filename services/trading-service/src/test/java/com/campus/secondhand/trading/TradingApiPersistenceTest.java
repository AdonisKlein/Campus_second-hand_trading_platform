package com.campus.secondhand.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
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
class TradingApiPersistenceTest {
    private static final String SECRET = "test-internal-jwt-secret-at-least-32-bytes";

    @Autowired MockMvc mvc;
    @Autowired TradeOrderRepository orders;
    @Autowired OutboxEventRepository outbox;
    @MockitoBean HttpAccountAdapter accounts;
    @MockitoBean HttpMarketplaceAdapter marketplace;

    @BeforeEach
    void cleanDatabase() {
        outbox.deleteAll();
        orders.deleteAll();
    }

    @Test
    void createAndAcceptOrderPersistStateThroughPublicApi() throws Exception {
        when(accounts.requireActiveStudent(2)).thenReturn(account(2, "buyer", "买家"));
        when(accounts.requireActiveStudent(7)).thenReturn(account(7, "seller", "卖家"));
        when(marketplace.require(10)).thenReturn(new MarketplacePort.ItemSnapshot(
                10, 7, "高等数学教材", new BigDecimal("18.00"), null, "ON_SALE", "VISIBLE"));

        String response = mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + jwt(2, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.buyerId").value(2))
                .andExpect(jsonPath("$.data.sellerId").value(7))
                .andReturn().getResponse().getContentAsString();

        TradeOrder stored = orders.findAll().getFirst();
        assertThat(response).contains("高等数学教材");
        assertThat(stored.getItemId()).isEqualTo(10);
        assertThat(stored.getBuyerNickname()).isEqualTo("买家");
        assertThat(stored.getStatus()).isEqualTo(OrderStatus.PURCHASE_REQUESTED);
        assertThat(outbox.count()).isZero();

        mvc.perform(post("/api/orders/" + stored.getId() + "/actions")
                        .header("Authorization", "Bearer " + jwt(7, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ACCEPT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PURCHASE_REQUESTED"));

        TradeOrder accepted = orders.findById(stored.getId()).orElseThrow();
        assertThat(accepted.getSagaState()).isEqualTo(SagaState.RESERVE_PENDING);
        assertThat(outbox.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getEventType()).isEqualTo("ItemReservationRequested");
            assertThat(event.getPayload()).contains("\"orderId\":" + stored.getId());
            assertThat(event.getPublishedAt()).isNull();
        });
    }

    private AccountPort.AccountSnapshot account(long id, String username, String nickname) {
        return new AccountPort.AccountSnapshot(id, username, nickname, "学院路校区", 100,
                "ACTIVE", "STUDENT", LocalDateTime.of(2026, 9, 3, 10, 0));
    }

    private String jwt(long userId, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("campus-gateway").subject(String.valueOf(userId))
                .claim("role", role).claim("auth_version", 1).issuedAt(now).expiresAt(now.plusSeconds(600)).build();
        JwtEncoder encoder = NimbusJwtEncoder.withSecretKey(
                new SecretKeySpec(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256")).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
