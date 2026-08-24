package com.campus.secondhand;

import com.campus.secondhand.item.ItemRepository;
import com.campus.secondhand.item.ItemStatus;
import com.campus.secondhand.item.ItemModerationStatus;
import com.campus.secondhand.item.SellerInventory;
import com.campus.secondhand.item.SellerInventoryRuleException;
import com.campus.secondhand.item.SellerItemAction;
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
import com.campus.secondhand.media.ProductImages;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
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
import org.springframework.mock.web.MockMultipartFile;

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
    @Autowired SellerInventory sellerInventory;
    @Autowired ProductImages productImages;
    @Autowired ObjectMapper objectMapper;
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
    void publicSearchCombinesKeywordAndCategory() throws Exception {
        User seller = saveUser("search-seller", "search-seller@example.com", "STUDENT");
        sellerInventory.publish(seller.getId(), new SellerInventory.ItemDraft(
            "Java 课程教材", "书籍", new java.math.BigDecimal("20.00"), "", ""));
        sellerInventory.publish(seller.getId(), new SellerInventory.ItemDraft(
            "Java 学习平板", "电子产品", new java.math.BigDecimal("300.00"), "", ""));
        sellerInventory.publish(seller.getId(), new SellerInventory.ItemDraft(
            "高等数学教材", "书籍", new java.math.BigDecimal("15.00"), "", ""));

        mvc.perform(get("/items").param("keyword", "Java").param("category", "书籍"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].title").value("Java 课程教材"));
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
    void productImageInterfaceRejectsUnsafeFilesAndServesNormalizedUpload() throws Exception {
        User seller = saveUser("seller", "seller@example.com", "STUDENT");
        User stranger = saveUser("stranger", "stranger@example.com", "STUDENT");
        User admin = saveUser("admin", "admin@example.com", "ADMIN");
        MockCookie sellerSession = login(seller.getEmail());
        MockCookie strangerSession = login(stranger.getEmail());
        MockCookie adminSession = login(admin.getEmail());

        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(20, 16,
            java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream imageBytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", imageBytes);
        byte[] png = imageBytes.toByteArray();

        mvc.perform(multipart("/media/product-images")
                .file(new MockMultipartFile("file", "photo.png", "image/png", png)).with(csrf()))
            .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/media/product-images")
                .file(new MockMultipartFile("file", "photo.png", "image/png", png)).cookie(adminSession).with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(multipart("/media/product-images")
                .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", "not an image".getBytes())).cookie(sellerSession).with(csrf()))
            .andExpect(status().isBadRequest());

        MvcResult uploaded = mvc.perform(multipart("/media/product-images")
                .file(new MockMultipartFile("file", "photo.png", "image/png", png)).cookie(sellerSession).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contentType").value("image/png"))
            .andExpect(jsonPath("$.data.width").value(20))
            .andReturn();
        String imageUrl = objectMapper.readTree(uploaded.getResponse().getContentAsString())
            .path("data").path("url").asText();

        mvc.perform(get(imageUrl))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/png"))
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")));
        mvc.perform(post("/items").cookie(strangerSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"盗用图片\",\"category\":\"其他\",\"price\":1,\"imageUrl\":\"%s\"}".formatted(imageUrl)))
            .andExpect(status().isConflict());
        mvc.perform(post("/items").cookie(sellerSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"安全图片\",\"category\":\"其他\",\"price\":1,\"imageUrl\":\"%s\"}".formatted(imageUrl)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.imageUrl").value(imageUrl));
        mvc.perform(post("/items").cookie(sellerSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"外链图片\",\"category\":\"其他\",\"price\":1,\"imageUrl\":\"https://tracker.example/a.jpg\"}"))
            .andExpect(status().isBadRequest());

        var tooLarge = new ProductImages.ImageUpload(new byte[5 * 1024 * 1024 + 1], "large.png", "image/png");
        com.campus.secondhand.media.ProductImageException exception = org.junit.jupiter.api.Assertions.assertThrows(
            com.campus.secondhand.media.ProductImageException.class,
            () -> productImages.store(seller.getId(), tooLarge));
        org.junit.jupiter.api.Assertions.assertEquals(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,
            exception.status());
        org.junit.jupiter.api.Assertions.assertThrows(com.campus.secondhand.media.ProductImageException.class,
            () -> productImages.store(0L, new ProductImages.ImageUpload(png, "photo.png", "image/png")));
    }

    @Test
    void sellerInventoryInterfaceOwnsLifecycleRules() {
        User seller = saveUser("seller", "seller@example.com", "STUDENT");
        User buyer = saveUser("buyer", "buyer@example.com", "STUDENT");
        User stranger = saveUser("stranger", "stranger@example.com", "STUDENT");
        User disabled = saveUser("disabled", "disabled@example.com", "STUDENT");
        User admin = saveUser("inventory-admin", "inventory-admin@example.com", "ADMIN");
        disabled.setStatus("DISABLED");
        users.saveAndFlush(disabled);
        var draft = new SellerInventory.ItemDraft(" 教材 ", "书籍", java.math.BigDecimal.valueOf(20), " 八成新 ", " ");
        var malformedImage = new SellerInventory.ItemDraft("教材", "书籍", java.math.BigDecimal.valueOf(20),
            null, "/media/product-images/" + seller.getId() + "/not-a-uuid.jpg");

        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class,
            () -> sellerInventory.publish(Long.MAX_VALUE, draft));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class,
            () -> sellerInventory.publish(disabled.getId(), draft));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class,
            () -> sellerInventory.publish(admin.getId(), draft));
        org.junit.jupiter.api.Assertions.assertThrows(SellerInventoryRuleException.class,
            () -> sellerInventory.publish(seller.getId(), malformedImage));

        var published = sellerInventory.publish(seller.getId(), draft);
        org.junit.jupiter.api.Assertions.assertEquals("教材", published.title());
        org.junit.jupiter.api.Assertions.assertNull(published.imageUrl());
        org.junit.jupiter.api.Assertions.assertEquals(List.of(SellerItemAction.WITHDRAW), published.allowedActions());
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class,
            () -> sellerInventory.revise(stranger.getId(), published.id(), draft));

        var withdrawn = sellerInventory.act(seller.getId(), published.id(), SellerItemAction.WITHDRAW);
        org.junit.jupiter.api.Assertions.assertEquals(ItemStatus.WITHDRAWN, withdrawn.status());
        org.junit.jupiter.api.Assertions.assertEquals(List.of(SellerItemAction.RELIST), withdrawn.allowedActions());

        var item = items.findById(published.id()).orElseThrow();
        item.setModerationStatus(ItemModerationStatus.REMOVED);
        items.saveAndFlush(item);
        org.junit.jupiter.api.Assertions.assertThrows(SellerInventoryRuleException.class,
            () -> sellerInventory.act(seller.getId(), published.id(), SellerItemAction.RELIST));

        item = items.findById(published.id()).orElseThrow();
        item.setModerationStatus(ItemModerationStatus.VISIBLE);
        items.saveAndFlush(item);
        sellerInventory.act(seller.getId(), published.id(), SellerItemAction.RELIST);
        tradingService.placeOrder(buyer.getId(), published.id());
        org.junit.jupiter.api.Assertions.assertThrows(SellerInventoryRuleException.class,
            () -> sellerInventory.revise(seller.getId(), published.id(), draft));
        org.junit.jupiter.api.Assertions.assertThrows(SellerInventoryRuleException.class,
            () -> sellerInventory.act(seller.getId(), published.id(), SellerItemAction.WITHDRAW));
    }

    @Test
    void mineAndSellerWritesUseSessionOwnerAndValidateInput() throws Exception {
        User seller = saveUser("seller", "seller@example.com", "STUDENT");
        User stranger = saveUser("stranger", "stranger@example.com", "STUDENT");
        MockCookie sellerSession = login(seller.getEmail());
        MockCookie strangerSession = login(stranger.getEmail());

        mvc.perform(get("/items/mine")).andExpect(status().isUnauthorized());
        MvcResult publish = mvc.perform(post("/items").cookie(sellerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"台灯\",\"category\":\"生活用品\",\"price\":25}"))
            .andExpect(status().isOk()).andReturn();
        Long itemId = items.findAll().getFirst().getId();

        mvc.perform(get("/items/mine").cookie(sellerSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].sellerId").value(seller.getId()))
            .andExpect(jsonPath("$.data[0].allowedActions[0]").value("WITHDRAW"));
        mvc.perform(put("/items/{id}", itemId).cookie(strangerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"冒充修改\",\"category\":\"其他\",\"price\":1}"))
            .andExpect(status().isForbidden());
        mvc.perform(put("/items/{id}", itemId).cookie(sellerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"修改后的台灯\",\"category\":\"生活用品\",\"price\":20}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("修改后的台灯"));
        mvc.perform(post("/items/{id}/seller-actions", itemId).cookie(sellerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"WITHDRAW\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("WITHDRAWN"));
        mvc.perform(get("/items")).andExpect(jsonPath("$.data", hasSize(0)));
        mvc.perform(post("/items").cookie(sellerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"负价商品\",\"category\":\"其他\",\"price\":-1}"))
            .andExpect(status().isBadRequest());
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
