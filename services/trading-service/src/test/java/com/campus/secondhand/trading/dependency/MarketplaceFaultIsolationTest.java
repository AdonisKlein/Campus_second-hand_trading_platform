package com.campus.secondhand.trading.dependency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campus.secondhand.trading.AccountPort;
import com.campus.secondhand.trading.OrderStatus;
import com.campus.secondhand.trading.TradeOrder;
import com.campus.secondhand.trading.TradeOrderRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.crypto.spec.SecretKeySpec;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketplaceFaultIsolationTest {
    private static final String SECRET = "test-internal-jwt-secret-at-least-32-bytes";
    private static final MockWebServer MARKETPLACE = new MockWebServer();
    private static final ConcurrentLinkedQueue<MockResponse> RESPONSES = new ConcurrentLinkedQueue<>();

    static {
        MARKETPLACE.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                MockResponse next = RESPONSES.poll();
                return next == null ? json(500) : next;
            }
        });
        try {
            MARKETPLACE.start();
        } catch (IOException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    @DynamicPropertySource
    static void marketplaceUri(DynamicPropertyRegistry registry) {
        registry.add("campus.trading.marketplace-uri",
                () -> MARKETPLACE.url("/").toString().replaceAll("/$", ""));
        registry.add("management.health.rabbit.enabled", () -> "false");
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:trading_fault_isolation;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    }

    @AfterAll
    static void stopMarketplace() throws Exception {
        MARKETPLACE.shutdown();
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TradeOrderRepository orders;
    @Autowired CircuitBreakerRegistry circuits;
    @MockitoBean AccountPort accounts;
    @MockitoBean com.campus.secondhand.trading.chat.AccountPort chatAccounts;

    @BeforeEach
    void reset() {
        circuits.circuitBreaker(MarketplaceDependencyClient.CIRCUIT_NAME).reset();
        jdbc.update("delete from outbox_events");
        jdbc.update("delete from inbox_events");
        jdbc.update("delete from trade_orders");
        RESPONSES.clear();
        when(accounts.requireActiveStudent(2L)).thenReturn(account(2, "买家"));
        when(accounts.find(anyLong())).thenAnswer(call -> Optional.of(account(call.getArgument(0), "用户")));
        when(chatAccounts.activeStudent(anyLong())).thenAnswer(call -> Optional.of(
                new com.campus.secondhand.trading.chat.AccountPort.Account(call.getArgument(0), "用户", "u")));
        drainRecorded();
    }

    @Test
    void configuredCircuitMatchesFaultIsolationContract() {
        CircuitBreakerConfig config = circuits.circuitBreaker(MarketplaceDependencyClient.CIRCUIT_NAME)
                .getCircuitBreakerConfig();
        assertThat(config.getSlidingWindowSize()).isEqualTo(10);
        assertThat(config.getMinimumNumberOfCalls()).isEqualTo(5);
        assertThat(config.getFailureRateThreshold()).isEqualTo(50f);
        assertThat(config.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(15_000L);
        assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(2);
    }

    @Test
    void failedPurchaseLeavesNoOrderOrOutboxAndPropagatesCorrelationId() throws Exception {
        RESPONSES.add(json(500));
        RESPONSES.add(json(500));
        mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + jwt(2))
                        .header("X-Correlation-Id", "fault-corr-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":10}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(header().string("X-Correlation-Id", "fault-corr-1"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PRODUCT_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("商品服务暂时不可用，请稍后重试"));
        assertThat(jdbc.queryForObject("select count(*) from trade_orders", Integer.class))
                .as("failed purchase must not persist trade_orders").isZero();
        assertThat(jdbc.queryForObject("select count(*) from outbox_events", Integer.class))
                .as("failed purchase must not persist outbox_events").isZero();
        RecordedRequest request = MARKETPLACE.takeRequest();
        RecordedRequest retry = MARKETPLACE.takeRequest();
        assertThat(request.getHeader("X-Correlation-Id")).isEqualTo("fault-corr-1");
        assertThat(retry.getHeader("X-Correlation-Id")).isEqualTo("fault-corr-1");
        assertThat(request.getHeader("X-Internal-Service-Token")).isEqualTo("test-internal-service-token-32-bytes");
    }

    @Test
    void existingOrdersRemainReadableAndProbesStayUpWhenMarketplaceFails() throws Exception {
        TradeOrder existing = seedOrder();
        RESPONSES.add(json(500));
        RESPONSES.add(json(500));
        mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + jwt(2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":11}"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + jwt(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(existing.getId()))
                .andExpect(jsonPath("$.data[0].status").value("PURCHASE_REQUESTED"));
        assertThat(jdbc.queryForObject("select count(*) from trade_orders", Integer.class)).isEqualTo(1);
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        String readiness = mvc.perform(get("/actuator/health/readiness")).andReturn().getResponse().getContentAsString();
        assertThat(readiness).doesNotContainIgnoringCase("circuitBreaker");
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void openCircuitDoesNotChangeLiveness() throws Exception {
        for (int i = 0; i < 12; i++) {
            RESPONSES.add(json(500));
        }
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/orders")
                            .header("Authorization", "Bearer " + jwt(2))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"itemId\":12}"))
                    .andExpect(status().isServiceUnavailable());
        }
        assertThat(circuits.circuitBreaker(MarketplaceDependencyClient.CIRCUIT_NAME).getState())
                .isEqualTo(CircuitBreaker.State.OPEN);
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        assertThat(jdbc.queryForObject("select count(*) from trade_orders", Integer.class)).isZero();
    }

    private TradeOrder seedOrder() {
        TradeOrder order = new TradeOrder();
        order.setItemId(9L);
        order.setBuyerId(2L);
        order.setSellerId(7L);
        order.setItemTitle("已有订单");
        order.setItemPrice(new BigDecimal("12.00"));
        order.setBuyerNickname("买家");
        order.setSellerNickname("卖家");
        order.setStatus(OrderStatus.PURCHASE_REQUESTED);
        order.setExpiresAt(LocalDateTime.now().plusDays(1));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return orders.saveAndFlush(order);
    }

    private AccountPort.AccountSnapshot account(long id, String name) {
        return new AccountPort.AccountSnapshot(id, "u" + id, name, "学院路校区", 100, "ACTIVE", "STUDENT",
                LocalDateTime.now());
    }

    private static MockResponse json(int status) {
        return new MockResponse().setResponseCode(status).setBody("{\"success\":false}")
                .addHeader("Content-Type", "application/json");
    }

    private void drainRecorded() {
        try {
            while (MARKETPLACE.takeRequest(1, java.util.concurrent.TimeUnit.MILLISECONDS) != null) {
                // Discard leftover recordings from previous tests.
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private String jwt(long user) {
        var now = java.time.Instant.now();
        var claims = JwtClaimsSet.builder().issuer("campus-gateway").subject(String.valueOf(user))
                .claim("role", "STUDENT").claim("auth_version", 1).issuedAt(now).expiresAt(now.plusSeconds(60)).build();
        JwtEncoder encoder = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(SECRET.getBytes(
                java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256")).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
