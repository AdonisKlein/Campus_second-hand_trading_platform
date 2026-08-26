package com.campus.secondhand.mysql;

import com.campus.secondhand.item.ItemModerationStatus;
import com.campus.secondhand.item.ItemRepository;
import com.campus.secondhand.item.ItemStatus;
import com.campus.secondhand.item.SellerInventory;
import com.campus.secondhand.order.OrderAction;
import com.campus.secondhand.order.OrderStatus;
import com.campus.secondhand.order.TradeOrderRepository;
import com.campus.secondhand.order.TradingService;
import com.campus.secondhand.report.ContentGovernance;
import com.campus.secondhand.report.ContentReportRepository;
import com.campus.secondhand.report.GovernanceAction;
import com.campus.secondhand.report.ReportActionRepository;
import com.campus.secondhand.report.ReportReason;
import com.campus.secondhand.report.ReportStatus;
import com.campus.secondhand.report.ReportTargetType;
import com.campus.secondhand.user.EmailVerification;
import com.campus.secondhand.user.EmailVerificationRepository;
import com.campus.secondhand.user.User;
import com.campus.secondhand.user.UserRepository;
import com.campus.secondhand.user.VerificationPurpose;
import com.campus.secondhand.user.VerificationService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class MySqlConcurrencyIT {
    private static final String PEPPER = "test-only-verification-pepper-at-least-32-bytes";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
        .withDatabaseName("campus_secondhand")
        .withUsername("campus")
        .withPassword("test-only-password");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired UserRepository users;
    @Autowired EmailVerificationRepository verifications;
    @Autowired VerificationService verificationService;
    @Autowired SellerInventory inventory;
    @Autowired ItemRepository items;
    @Autowired TradingService trading;
    @Autowired TradeOrderRepository orders;
    @Autowired ContentGovernance governance;
    @Autowired ContentReportRepository reports;
    @Autowired ReportActionRepository reportActions;
    @Autowired PasswordEncoder passwords;

    @BeforeEach
    void cleanBusinessData() {
        for (String table : List.of("report_actions", "content_reports", "chat_messages", "chat_blocks",
            "chat_conversations", "messages", "trade_orders", "item_tags", "items", "email_verification",
            "SPRING_SESSION_ATTRIBUTES", "SPRING_SESSION", "users")) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    @Test
    void freshSchemaValidatesAndJdbcSessionRowsCascade() {
        flyway.validate();
        assertEquals(5, jdbc.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class));
        long now = Instant.now().toEpochMilli();
        jdbc.update("INSERT INTO SPRING_SESSION "
                + "(PRIMARY_ID, SESSION_ID, CREATION_TIME, LAST_ACCESS_TIME, MAX_INACTIVE_INTERVAL, EXPIRY_TIME) "
                + "VALUES (?, ?, ?, ?, ?, ?)",
            "00000000-0000-0000-0000-000000000001", "00000000-0000-0000-0000-000000000002",
            now, now, 1800, now + 1_800_000);
        jdbc.update("INSERT INTO SPRING_SESSION_ATTRIBUTES "
                + "(SESSION_PRIMARY_ID, ATTRIBUTE_NAME, ATTRIBUTE_BYTES) VALUES (?, ?, ?)",
            "00000000-0000-0000-0000-000000000001", "smoke", new byte[] {1, 2, 3});
        jdbc.update("DELETE FROM SPRING_SESSION WHERE PRIMARY_ID = ?",
            "00000000-0000-0000-0000-000000000001");
        assertEquals(0, jdbc.queryForObject(
            "SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES WHERE ATTRIBUTE_NAME = 'smoke'", Integer.class));
    }

    @Test
    void concurrentSameEmailRegistrationPersistsExactlyOneUser() throws Exception {
        List<Boolean> outcomes = race(
            () -> persistUser("same-email-a", "same-email@example.com", "STUDENT"),
            () -> persistUser("same-email-b", "same-email@example.com", "STUDENT"));
        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, users.findAll().stream().filter(u -> u.getEmail().equals("same-email@example.com")).count());
    }

    @Test
    void concurrentVerificationConsumesCodeExactlyOnce() throws Exception {
        String email = "one-code@example.com";
        String code = "123456";
        EmailVerification challenge = new EmailVerification();
        challenge.setEmail(email);
        challenge.setPurpose(VerificationPurpose.RESET_PASSWORD);
        challenge.setCodeHash(hash(email, VerificationPurpose.RESET_PASSWORD, code));
        challenge.setCreatedAt(LocalDateTime.now());
        challenge.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        verifications.saveAndFlush(challenge);

        List<Boolean> outcomes = race(
            () -> verificationService.verifyCode(email, VerificationPurpose.RESET_PASSWORD, code),
            () -> verificationService.verifyCode(email, VerificationPurpose.RESET_PASSWORD, code));
        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM email_verification WHERE email = ? AND purpose = ? AND used = true",
            Integer.class, email, VerificationPurpose.RESET_PASSWORD.name()));
    }

    @Test
    void concurrentBuyersCanRequestButSellerCanSelectOnlyOne() throws Exception {
        User seller = savedUser("seller", "seller@example.com", "STUDENT");
        User buyerA = savedUser("buyer-a", "buyer-a@example.com", "STUDENT");
        User buyerB = savedUser("buyer-b", "buyer-b@example.com", "STUDENT");
        var item = inventory.publish(seller.getId(), new SellerInventory.ItemDraft(
            "并发交易教材", "书籍", BigDecimal.valueOf(25), "并发验收", ""));

        List<Boolean> requested = race(
            () -> { trading.requestPurchase(buyerA.getId(), item.id()); return true; },
            () -> { trading.requestPurchase(buyerB.getId(), item.id()); return true; });
        assertEquals(2, requested.stream().filter(Boolean::booleanValue).count());
        assertEquals(ItemStatus.ON_SALE, items.findById(item.id()).orElseThrow().getStatus());
        List<Long> orderIds = orders.findAll().stream().map(order -> order.getId()).toList();

        List<Boolean> selected = race(
            () -> { trading.perform(seller.getId(), orderIds.get(0), OrderAction.ACCEPT); return true; },
            () -> { trading.perform(seller.getId(), orderIds.get(1), OrderAction.ACCEPT); return true; });
        assertEquals(1, selected.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, orders.findAll().stream().filter(o -> o.getStatus() == OrderStatus.WAITING_HANDOVER).count());
        assertEquals(1, orders.findAll().stream().filter(o -> o.getStatus() == OrderStatus.DECLINED).count());
        assertEquals(ItemStatus.RESERVED, items.findById(item.id()).orElseThrow().getStatus());
    }

    @Test
    void concurrentAdminsCanResolveReportExactlyOnce() throws Exception {
        User seller = savedUser("reported-seller", "reported-seller@example.com", "STUDENT");
        User reporter = savedUser("reporter", "reporter@example.com", "STUDENT");
        User adminA = savedUser("admin-a", "admin-a@example.com", "ADMIN");
        User adminB = savedUser("admin-b", "admin-b@example.com", "ADMIN");
        var item = inventory.publish(seller.getId(), new SellerInventory.ItemDraft(
            "违规商品", "其他", BigDecimal.ONE, "用于并发治理验收", ""));
        var report = governance.submit(reporter.getId(), new ContentGovernance.ReportDraft(
            ReportTargetType.ITEM, item.id(), ReportReason.PROHIBITED_CONTENT, "该商品包含禁止发布的内容"));
        var decision = new ContentGovernance.Decision(
            ReportStatus.RESOLVED, GovernanceAction.REMOVE_ITEM, "审核确认后执行下架");

        List<Boolean> outcomes = race(
            () -> { governance.decide(adminA.getId(), report.id(), decision); return true; },
            () -> { governance.decide(adminB.getId(), report.id(), decision); return true; });
        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        assertEquals(ReportStatus.RESOLVED, reports.findById(report.id()).orElseThrow().getStatus());
        assertEquals(1, reportActions.findByReportIdOrderByCreatedAtAsc(report.id()).size());
        assertEquals(ItemModerationStatus.REMOVED, items.findById(item.id()).orElseThrow().getModerationStatus());
    }

    private boolean persistUser(String username, String email, String role) {
        try {
            savedUser(username, email, role);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private User savedUser(String username, String email, String role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setNickname(username);
        user.setRole(role);
        user.setPasswordHash(passwords.encode("abc123"));
        return users.saveAndFlush(user);
    }

    private List<Boolean> race(Callable<Boolean> first, Callable<Boolean> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var left = executor.submit(() -> runWhenReleased(ready, start, first));
            var right = executor.submit(() -> runWhenReleased(ready, start, second));
            ready.await();
            start.countDown();
            return List.of(left.get(), right.get());
        }
    }

    private boolean runWhenReleased(CountDownLatch ready, CountDownLatch start, Callable<Boolean> action) {
        ready.countDown();
        try {
            start.await();
            return action.call();
        } catch (Exception exception) {
            return false;
        }
    }

    private String hash(String email, VerificationPurpose purpose, String code) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(PEPPER.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((purpose + "\0" + email + "\0" + code)
            .getBytes(StandardCharsets.UTF_8)));
    }
}
