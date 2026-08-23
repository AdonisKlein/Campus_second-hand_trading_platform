package com.campus.secondhand;

import com.campus.secondhand.item.ItemRepository;
import com.campus.secondhand.item.ItemStatus;
import com.campus.secondhand.message.MessageRepository;
import com.campus.secondhand.order.TradeOrderRepository;
import com.campus.secondhand.order.OrderStatus;
import com.campus.secondhand.order.OrderAction;
import com.campus.secondhand.order.TradingService;
import com.campus.secondhand.user.EmailVerificationRepository;
import com.campus.secondhand.user.User;
import com.campus.secondhand.user.UserRepository;
import com.campus.secondhand.user.EmailVerification;
import com.campus.secondhand.user.VerificationPurpose;
import com.campus.secondhand.user.VerificationService;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockCookie;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecondhandApplicationTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ItemRepository items;
    @Autowired MessageRepository messages;
    @Autowired TradeOrderRepository orders;
    @Autowired EmailVerificationRepository verifications;
    @Autowired PasswordEncoder passwords;
    @Autowired VerificationService verificationService;
    @Autowired TradingService tradingService;
    @MockitoBean JavaMailSender mailSender;

    @BeforeEach
    void clean() {
        orders.deleteAll();
        messages.deleteAll();
        items.deleteAll();
        verifications.deleteAll();
        users.deleteAll();
    }

    @Test
    void publicCanBrowseButCannotPublish() throws Exception {
        mvc.perform(get("/items")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/liveness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(post("/items").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"教材\",\"category\":\"书籍\",\"price\":20}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void loginCreatesServerSessionAndLogoutInvalidatesIt() throws Exception {
        saveUser("student", "student@example.com", "STUDENT");
        MockCookie session = login("student@example.com");
        mvc.perform(get("/users/me").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.email").value("student@example.com"));
        mvc.perform(post("/auth/logout").cookie(session).with(csrf()))
            .andExpect(status().isOk());
        mvc.perform(get("/users/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void loginFailureDoesNotRevealWhetherEmailExists() throws Exception {
        saveUser("student", "student@example.com", "STUDENT");
        String body = "{\"email\":\"%s\",\"password\":\"wrong123\"}";
        mvc.perform(post("/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body.formatted("student@example.com")))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("邮箱或密码错误"));
        mvc.perform(post("/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body.formatted("missing@example.com")))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("邮箱或密码错误"));
    }

    @Test
    void publisherIdentityComesFromSessionEvenWhenBodyIsForged() throws Exception {
        User seller = saveUser("seller", "seller@example.com", "STUDENT");
        User victim = saveUser("victim", "victim@example.com", "STUDENT");
        MockCookie session = login(seller.getEmail());
        mvc.perform(post("/items").cookie(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"教材\",\"category\":\"书籍\",\"price\":20,\"sellerId\":%d}".formatted(victim.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sellerId").value(seller.getId()));
    }

    @Test
    void studentCannotBecomeAdminBySubmittingAdminId() throws Exception {
        User admin = saveUser("admin", "admin@example.com", "ADMIN");
        User student = saveUser("student", "student@example.com", "STUDENT");
        MockCookie session = login(student.getEmail());
        mvc.perform(get("/admin/users").param("adminId", admin.getId().toString()).cookie(session))
            .andExpect(status().isForbidden());
    }

    @Test
    void disabledAdminLosesAccessOnExistingSession() throws Exception {
        User admin = saveUser("admin", "admin@example.com", "ADMIN");
        MockCookie session = login(admin.getEmail());
        mvc.perform(get("/admin/users").cookie(session)).andExpect(status().isOk());
        admin.setStatus("DISABLED");
        users.saveAndFlush(admin);
        mvc.perform(get("/admin/users").cookie(session)).andExpect(status().isForbidden());
    }

    @Test
    void authVersionChangeInvalidatesEveryExistingSession() throws Exception {
        User student = saveUser("student", "student@example.com", "STUDENT");
        MockCookie session = login(student.getEmail());
        student.setAuthVersion(student.getAuthVersion() + 1);
        users.saveAndFlush(student);
        mvc.perform(get("/users/me").cookie(session)).andExpect(status().isForbidden());
    }

    @Test
    void restoredAccountDoesNotRestoreOldSession() throws Exception {
        User admin = saveUser("admin", "admin@example.com", "ADMIN");
        User student = saveUser("student", "student@example.com", "STUDENT");
        MockCookie adminSession = login(admin.getEmail());
        MockCookie oldStudentSession = login(student.getEmail());
        mvc.perform(put("/admin/users/{id}/status", student.getId()).cookie(adminSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}"))
            .andExpect(status().isOk());
        mvc.perform(put("/admin/users/{id}/status", student.getId()).cookie(adminSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/users/me").cookie(oldStudentSession)).andExpect(status().isForbidden());
    }

    @Test
    void resendingCodeResetsUsedAndAttemptCount() {
        EmailVerification challenge = new EmailVerification();
        challenge.setEmail("code@example.com");
        challenge.setPurpose(VerificationPurpose.REGISTER);
        challenge.setCodeHash("0".repeat(64));
        challenge.setAttempts(5);
        challenge.setUsed(true);
        challenge.setCreatedAt(LocalDateTime.now().minusMinutes(2));
        challenge.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        verifications.saveAndFlush(challenge);
        verificationService.sendCode("code@example.com", VerificationPurpose.REGISTER);
        EmailVerification refreshed = verifications.findAll().getFirst();
        org.junit.jupiter.api.Assertions.assertFalse(refreshed.isUsed());
        org.junit.jupiter.api.Assertions.assertEquals(0, refreshed.getAttempts());
    }

    @Test
    void orderWorkflowUsesSessionIdentitySnapshotsAndRoleSpecificActions() throws Exception {
        User seller = saveUser("seller", "seller@example.com", "STUDENT");
        User buyer = saveUser("buyer", "buyer@example.com", "STUDENT");
        MockCookie sellerSession = login(seller.getEmail());
        mvc.perform(post("/items").cookie(sellerSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"教材\",\"category\":\"书籍\",\"price\":20}"));
        Long itemId = items.findAll().getFirst().getId();
        MockCookie buyerSession = login(buyer.getEmail());
        mvc.perform(post("/orders").cookie(buyerSession).contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\":%d}".formatted(itemId)))
            .andExpect(status().isForbidden());
        mvc.perform(post("/orders").cookie(buyerSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\":%d,\"buyerId\":%d}".formatted(itemId, seller.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.buyerId").value(buyer.getId()))
            .andExpect(jsonPath("$.data.status").value("PENDING_SELLER_CONFIRMATION"))
            .andExpect(jsonPath("$.data.allowedActions[0]").value("CANCEL"));
        Long orderId = orders.findAll().getFirst().getId();

        mvc.perform(post("/orders/{id}/actions", orderId).cookie(sellerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("WAITING_HANDOVER"));
        mvc.perform(post("/orders/{id}/actions", orderId).cookie(buyerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"COMPLETE\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        var item = items.findById(itemId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(ItemStatus.SOLD, item.getStatus());
        item.setTitle("后来修改的标题");
        items.saveAndFlush(item);
        mvc.perform(get("/orders").cookie(buyerSession))
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].itemTitle").value("教材"))
            .andExpect(jsonPath("$.data[0].allowedActions", hasSize(0)));
    }

    @Test
    void expiredReservationReleasesItemForAnotherBuyer() {
        User seller = saveUser("seller", "seller@example.com", "STUDENT");
        User buyer = saveUser("buyer", "buyer@example.com", "STUDENT");
        var item = new com.campus.secondhand.item.Item();
        item.setTitle("教材");
        item.setCategory("书籍");
        item.setPrice(java.math.BigDecimal.valueOf(20));
        item.setSellerId(seller.getId());
        item = items.saveAndFlush(item);

        tradingService.placeOrder(buyer.getId(), item.getId());
        var order = orders.findAll().getFirst();
        order.setReservationExpiresAt(LocalDateTime.now().minusSeconds(1));
        orders.saveAndFlush(order);

        org.junit.jupiter.api.Assertions.assertTrue(tradingService.expireReservation(order.getId()));
        org.junit.jupiter.api.Assertions.assertEquals(OrderStatus.EXPIRED,
            orders.findById(order.getId()).orElseThrow().getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(ItemStatus.ON_SALE,
            items.findById(item.getId()).orElseThrow().getStatus());
    }

    @Test
    void expiredActionCommitsReleaseAndReturnsNoAllowedActions() {
        User seller = saveUser("seller", "seller@example.com", "STUDENT");
        User buyer = saveUser("buyer", "buyer@example.com", "STUDENT");
        User stranger = saveUser("stranger", "stranger@example.com", "STUDENT");
        var item = new com.campus.secondhand.item.Item();
        item.setTitle("教材");
        item.setCategory("书籍");
        item.setPrice(java.math.BigDecimal.valueOf(20));
        item.setSellerId(seller.getId());
        item = items.saveAndFlush(item);

        tradingService.placeOrder(buyer.getId(), item.getId());
        var order = orders.findAll().getFirst();
        order.setReservationExpiresAt(LocalDateTime.now().minusSeconds(1));
        orders.saveAndFlush(order);

        org.junit.jupiter.api.Assertions.assertThrows(
            com.campus.secondhand.order.TradingRuleException.class,
            () -> tradingService.perform(stranger.getId(), order.getId(), OrderAction.ACCEPT));
        org.junit.jupiter.api.Assertions.assertEquals(OrderStatus.PENDING_SELLER_CONFIRMATION,
            orders.findById(order.getId()).orElseThrow().getStatus());

        var result = tradingService.perform(seller.getId(), order.getId(), OrderAction.ACCEPT);
        org.junit.jupiter.api.Assertions.assertEquals(OrderStatus.EXPIRED, result.status());
        org.junit.jupiter.api.Assertions.assertTrue(result.allowedActions().isEmpty());
        org.junit.jupiter.api.Assertions.assertEquals(OrderStatus.EXPIRED,
            orders.findById(order.getId()).orElseThrow().getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(ItemStatus.ON_SALE,
            items.findById(item.getId()).orElseThrow().getStatus());
    }

    private User saveUser(String username, String email, String role) {
        User user = new User();
        user.setUsername(username + UUID.randomUUID().toString().substring(0, 6));
        user.setEmail(email);
        user.setNickname(username);
        user.setRole(role);
        user.setPasswordHash(passwords.encode("abc123"));
        return users.saveAndFlush(user);
    }

    private MockCookie login(String email) throws Exception {
        MvcResult result = mvc.perform(post("/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"abc123\"}".formatted(email)))
            .andExpect(status().isOk()).andReturn();
        return (MockCookie) result.getResponse().getCookie("SESSION");
    }
}
