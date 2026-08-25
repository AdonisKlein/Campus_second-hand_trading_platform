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
import com.campus.secondhand.order.TradingRuleException;
import com.campus.secondhand.order.TradeDesk;
import com.campus.secondhand.user.EmailVerificationRepository;
import com.campus.secondhand.user.User;
import com.campus.secondhand.user.UserRepository;
import com.campus.secondhand.user.EmailVerification;
import com.campus.secondhand.user.VerificationPurpose;
import com.campus.secondhand.user.VerificationService;
import com.campus.secondhand.media.ProductImages;
import com.campus.secondhand.report.ContentReportRepository;
import com.campus.secondhand.report.ReportActionRepository;
import com.campus.secondhand.chat.ChatConversationRepository;
import com.campus.secondhand.chat.ChatMessageRepository;
import com.campus.secondhand.chat.ChatBlockRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.Set;
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
    @Autowired ContentReportRepository contentReports;
    @Autowired ReportActionRepository reportActions;
    @Autowired ChatConversationRepository chatConversations;
    @Autowired ChatMessageRepository chatMessages;
    @Autowired ChatBlockRepository chatBlocks;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean JavaMailSender mailSender;

    @BeforeEach
    void clean() {
        chatMessages.deleteAll();
        chatBlocks.deleteAll();
        chatConversations.deleteAll();
        reportActions.deleteAll();
        contentReports.deleteAll();
        orders.deleteAll();
        messages.deleteAll();
        items.deleteAll();
        verifications.deleteAll();
        users.deleteAll();
    }

    @Test
    void smtpFailureReturnsServiceUnavailableAndInvalidatesChallenge() throws Exception {
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailAuthenticationException("provider detail"))
            .when(mailSender).send(org.mockito.ArgumentMatchers.any(org.springframework.mail.SimpleMailMessage.class));

        mvc.perform(post("/auth/verification/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"smtp-failure@example.com\"}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.message").value("邮件服务暂时不可用，请稍后重试"));

        EmailVerification challenge = verifications.findAll().stream()
            .filter(candidate -> candidate.getEmail().equals("smtp-failure@example.com"))
            .findFirst().orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(challenge.isUsed());
    }

    @Test
    void directChatIsPrivateUnreadAndBlockable() throws Exception {
        User seller = saveUser("chat-seller", "chat-seller@example.com", "STUDENT");
        User buyer = saveUser("chat-buyer", "chat-buyer@example.com", "STUDENT");
        User stranger = saveUser("chat-stranger", "chat-stranger@example.com", "STUDENT");
        User admin = saveUser("chat-admin", "chat-admin@example.com", "ADMIN");
        var item = sellerInventory.publish(seller.getId(), new SellerInventory.ItemDraft(
            "私聊测试教材", "书籍", java.math.BigDecimal.valueOf(18), "仅公开商品信息", ""));
        MockCookie buyerSession = login(buyer.getEmail());
        MockCookie sellerSession = login(seller.getEmail());
        MockCookie strangerSession = login(stranger.getEmail());
        MockCookie adminSession = login(admin.getEmail());

        MvcResult opened = mvc.perform(post("/chat/conversations").cookie(buyerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\":%d,\"buyerId\":%d,\"sellerId\":%d}".formatted(item.id(), stranger.getId(), stranger.getId())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.otherUserId").value(seller.getId())).andReturn();
        String conversationId = objectMapper.readTree(opened.getResponse().getContentAsString()).at("/data/id").asText();
        org.junit.jupiter.api.Assertions.assertTrue(conversationId.matches("[0-9a-f-]{36}"));
        mvc.perform(post("/chat/conversations").cookie(buyerSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\":%d}".formatted(item.id())))
            .andExpect(jsonPath("$.data.id").value(conversationId));

        mvc.perform(get("/chat/conversations/{id}/messages", conversationId).cookie(strangerSession))
            .andExpect(status().isForbidden());
        mvc.perform(get("/chat/conversations").cookie(adminSession)).andExpect(status().isForbidden());
        mvc.perform(post("/chat/conversations/{id}/messages", conversationId).cookie(buyerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"这条只应由买卖双方看到\",\"senderId\":%d}".formatted(stranger.getId())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.senderId").value(buyer.getId()))
            .andExpect(jsonPath("$.data.sequence").value(1));
        mvc.perform(get("/chat/conversations").cookie(sellerSession))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalUnread").value(1))
            .andExpect(jsonPath("$.data.conversations[0].unreadCount").value(1));
        mvc.perform(post("/chat/conversations/{id}/read", conversationId).cookie(sellerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"throughSequence\":1}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.unreadCount").value(0));
        mvc.perform(put("/chat/blocks/{id}", buyer.getId()).cookie(sellerSession).with(csrf()))
            .andExpect(status().isOk());
        mvc.perform(post("/chat/conversations/{id}/messages", conversationId).cookie(buyerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"屏蔽后不能发送\"}"))
            .andExpect(status().isConflict());
        mvc.perform(delete("/chat/blocks/{id}", buyer.getId()).cookie(sellerSession).with(csrf()))
            .andExpect(status().isOk());
        mvc.perform(post("/chat/conversations/{id}/messages", conversationId).cookie(sellerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"解除后可以回复\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.sequence").value(2));
        mvc.perform(get("/messages/item/{id}", item.id()))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("这条只应由买卖双方看到"))));
    }

    @Test
    void reportWorkflowUsesSessionIdentityAndAdminDecisionRemovesItem() throws Exception {
        User seller = saveUser("report-seller", "report-seller@example.com", "STUDENT");
        User reporter = saveUser("reporter", "reporter@example.com", "STUDENT");
        User admin = saveUser("report-admin", "report-admin@example.com", "ADMIN");
        var item = sellerInventory.publish(seller.getId(), new SellerInventory.ItemDraft(
            "疑似虚假商品", "其他", java.math.BigDecimal.TEN, "需要管理员核查", ""));
        MockCookie reporterSession = login(reporter.getEmail());
        MockCookie adminSession = login(admin.getEmail());

        String body = "{\"targetType\":\"ITEM\",\"targetId\":%d,\"reasonCode\":\"FRAUD\",\"description\":\"商品描述与实际情况明显不一致，请核查\",\"reporterId\":%d}"
            .formatted(item.id(), seller.getId());
        mvc.perform(post("/reports").cookie(reporterSession).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reporterId").value(reporter.getId()))
            .andExpect(jsonPath("$.data.status").value("OPEN"));
        mvc.perform(post("/reports").cookie(reporterSession).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict());
        mvc.perform(get("/admin/reports").cookie(reporterSession)).andExpect(status().isForbidden());
        mvc.perform(get("/reports/mine").cookie(reporterSession))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.reports", hasSize(1)));
        Long reportId = contentReports.findAll().getFirst().getId();

        mvc.perform(put("/admin/reports/{id}", reportId).cookie(adminSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\",\"action\":\"REMOVE_ITEM\",\"note\":\"核查成立，商品已下架\",\"adminId\":999}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("RESOLVED"))
            .andExpect(jsonPath("$.data.history", hasSize(1)));
        org.junit.jupiter.api.Assertions.assertEquals(ItemModerationStatus.REMOVED,
            items.findById(item.id()).orElseThrow().getModerationStatus());
        org.junit.jupiter.api.Assertions.assertEquals(admin.getId(), reportActions.findAll().getFirst().getAdminId());
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
    void campusSearchSupportsMultipleTermsFiltersAndSafeUserResults() throws Exception {
        User seller = saveUser("search-owner", "search-owner@example.com", "STUDENT");
        seller.setNickname("林同学");
        seller.setCampusRegion("沙河校区");
        seller.setCreditScore(108);
        users.save(seller);
        sellerInventory.publish(seller.getId(), new SellerInventory.ItemDraft(
            "Java 学习平板", "电子产品", new java.math.BigDecimal("320.00"), "附第七版教材电子笔记", "",
            "沙河校区", Set.of("支持验货", "可小刀")));
        sellerInventory.publish(seller.getId(), new SellerInventory.ItemDraft(
            "Java 课程教材", "书籍", new java.math.BigDecimal("20.00"), "纸质书", "",
            "学院路校区", Set.of("仅自提")));

        mvc.perform(get("/search").param("scope", "ITEMS").param("q", "Java 第七版")
                .param("minPrice", "300").param("maxPrice", "350").param("region", "沙河校区")
                .param("tags", "支持验货"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items", hasSize(1)))
            .andExpect(jsonPath("$.data.items[0].title").value("Java 学习平板"))
            .andExpect(jsonPath("$.data.items[0].sellerCreditScore").value(108));

        mvc.perform(get("/search").param("scope", "USERS").param("q", "林同学"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.users", hasSize(1)))
            .andExpect(jsonPath("$.data.users[0].nickname").value("林同学"))
            .andExpect(jsonPath("$.data.users[0].email").doesNotExist())
            .andExpect(jsonPath("$.data.users[0].phone").doesNotExist());

        mvc.perform(get("/search").param("minPrice", "50").param("maxPrice", "20"))
            .andExpect(status().isBadRequest());
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
        var request = tradingService.requestPurchase(buyer.getId(), published.id());
        sellerInventory.revise(seller.getId(), published.id(), draft);
        tradingService.perform(seller.getId(), request.id(), OrderAction.ACCEPT);
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
        User stranger = saveUser("order-stranger", "order-stranger@example.com", "STUDENT");
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
            .andExpect(jsonPath("$.data.status").value("PURCHASE_REQUESTED"))
            .andExpect(jsonPath("$.data.allowedActions[0]").value("CANCEL"));
        Long orderId = orders.findAll().getFirst().getId();
        org.junit.jupiter.api.Assertions.assertEquals(ItemStatus.ON_SALE, items.findById(itemId).orElseThrow().getStatus());
        mvc.perform(get("/items")).andExpect(jsonPath("$.data", hasSize(1)));

        mvc.perform(post("/orders/{id}/actions", orderId).cookie(sellerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("WAITING_HANDOVER"));
        mvc.perform(post("/chat/order-conversations").cookie(sellerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":%d}".formatted(orderId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.otherUserId").value(buyer.getId()));
        mvc.perform(post("/chat/order-conversations").cookie(login(stranger.getEmail())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":%d}".formatted(orderId)))
            .andExpect(status().isForbidden());
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
    void orderDeskGroupsSellerRequestsAndReturnsOnlyPublicCounterpartyData() throws Exception {
        User seller = saveUser("desk-seller", "desk-seller@example.com", "STUDENT");
        User buyerA = saveUser("desk-a", "desk-a@example.com", "STUDENT");
        buyerA.setNickname("学院路买家"); buyerA.setCampusRegion("学院路校区"); buyerA.setCreditScore(96);
        users.saveAndFlush(buyerA);
        User buyerB = saveUser("desk-b", "desk-b@example.com", "STUDENT");
        buyerB.setNickname("沙河买家"); buyerB.setCampusRegion("沙河校区"); buyerB.setCreditScore(91);
        users.saveAndFlush(buyerB);
        var item = sellerInventory.publish(seller.getId(), new SellerInventory.ItemDraft(
            "订单工作台教材", "书籍", java.math.BigDecimal.valueOf(35), "九成新", ""));
        tradingService.requestPurchase(buyerA.getId(), item.id());
        tradingService.requestPurchase(buyerB.getId(), item.id());

        var desk = tradingService.browse(seller.getId(), TradeDesk.Perspective.SELLING, TradeDesk.Stage.REQUESTS);
        org.junit.jupiter.api.Assertions.assertEquals(2, desk.summary().requests());
        org.junit.jupiter.api.Assertions.assertEquals(2, desk.summary().actionable());
        org.junit.jupiter.api.Assertions.assertEquals(1, desk.groups().size());
        org.junit.jupiter.api.Assertions.assertEquals(2, desk.groups().getFirst().entries().size());
        org.junit.jupiter.api.Assertions.assertEquals(4, desk.groups().getFirst().entries().getFirst().timeline().size());

        MockCookie sellerSession = login(seller.getEmail());
        mvc.perform(get("/orders/desk").cookie(sellerSession)
                .param("perspective", "SELLING").param("stage", "REQUESTS"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.summary.requests").value(2))
            .andExpect(jsonPath("$.data.groups", hasSize(1)))
            .andExpect(jsonPath("$.data.groups[0].entries", hasSize(2)))
            .andExpect(jsonPath("$.data.groups[0].entries[0].counterparty.campusRegion").exists())
            .andExpect(jsonPath("$.data.groups[0].entries[0].counterparty.creditScore").exists())
            .andExpect(jsonPath("$.data.groups[0].entries[0].counterparty.email").doesNotExist())
            .andExpect(jsonPath("$.data.groups[0].entries[0].counterparty.phone").doesNotExist());
    }

    @Test
    void expiredPurchaseRequestNeverHidesOrReservesItem() {
        User seller = saveUser("seller", "seller@example.com", "STUDENT");
        User buyer = saveUser("buyer", "buyer@example.com", "STUDENT");
        var item = new com.campus.secondhand.item.Item();
        item.setTitle("教材");
        item.setCategory("书籍");
        item.setPrice(java.math.BigDecimal.valueOf(20));
        item.setSellerId(seller.getId());
        item = items.saveAndFlush(item);

        tradingService.requestPurchase(buyer.getId(), item.getId());
        var order = orders.findAll().getFirst();
        order.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        orders.saveAndFlush(order);

        org.junit.jupiter.api.Assertions.assertTrue(tradingService.expireOrder(order.getId()));
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

        tradingService.requestPurchase(buyer.getId(), item.getId());
        var order = orders.findAll().getFirst();
        order.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        orders.saveAndFlush(order);

        org.junit.jupiter.api.Assertions.assertThrows(
            com.campus.secondhand.order.TradingRuleException.class,
            () -> tradingService.perform(stranger.getId(), order.getId(), OrderAction.ACCEPT));
        org.junit.jupiter.api.Assertions.assertEquals(OrderStatus.PURCHASE_REQUESTED,
            orders.findById(order.getId()).orElseThrow().getStatus());

        var result = tradingService.perform(seller.getId(), order.getId(), OrderAction.ACCEPT);
        org.junit.jupiter.api.Assertions.assertEquals(OrderStatus.EXPIRED, result.status());
        org.junit.jupiter.api.Assertions.assertTrue(result.allowedActions().isEmpty());
        org.junit.jupiter.api.Assertions.assertEquals(OrderStatus.EXPIRED,
            orders.findById(order.getId()).orElseThrow().getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(ItemStatus.ON_SALE,
            items.findById(item.getId()).orElseThrow().getStatus());
    }

    @Test
    void sellerChoosesOneBuyerAndOnlyThenReservesItem() {
        User seller = saveUser("choice-seller", "choice-seller@example.com", "STUDENT");
        User buyerA = saveUser("choice-a", "choice-a@example.com", "STUDENT");
        User buyerB = saveUser("choice-b", "choice-b@example.com", "STUDENT");
        var item = new com.campus.secondhand.item.Item();
        item.setTitle("多人想要的教材"); item.setCategory("书籍"); item.setPrice(java.math.BigDecimal.valueOf(30)); item.setSellerId(seller.getId());
        item = items.saveAndFlush(item);
        Long choiceItemId = item.getId();

        var requestA = tradingService.requestPurchase(buyerA.getId(), choiceItemId);
        var requestB = tradingService.requestPurchase(buyerB.getId(), choiceItemId);
        org.junit.jupiter.api.Assertions.assertEquals(OrderStatus.PURCHASE_REQUESTED, requestA.status());
        org.junit.jupiter.api.Assertions.assertEquals(OrderStatus.PURCHASE_REQUESTED, requestB.status());
        org.junit.jupiter.api.Assertions.assertEquals(ItemStatus.ON_SALE, items.findById(choiceItemId).orElseThrow().getStatus());
        org.junit.jupiter.api.Assertions.assertThrows(TradingRuleException.class,
            () -> tradingService.requestPurchase(buyerA.getId(), choiceItemId));

        var accepted = tradingService.perform(seller.getId(), requestB.id(), OrderAction.ACCEPT);
        org.junit.jupiter.api.Assertions.assertEquals(OrderStatus.WAITING_HANDOVER, accepted.status());
        org.junit.jupiter.api.Assertions.assertEquals(ItemStatus.RESERVED, items.findById(choiceItemId).orElseThrow().getStatus());
        var declined = orders.findById(requestA.id()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(OrderStatus.DECLINED, declined.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("卖家已选择其他买家", declined.getClosureReason());

        declined = orders.findById(requestB.id()).orElseThrow();
        declined.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        orders.saveAndFlush(declined);
        org.junit.jupiter.api.Assertions.assertTrue(tradingService.expireOrder(declined.getId()));
        org.junit.jupiter.api.Assertions.assertEquals(ItemStatus.ON_SALE, items.findById(choiceItemId).orElseThrow().getStatus());
    }

    @Test
    void productDetailProjectionKeepsPublicSellerDataAndViewerActionsTogether() throws Exception {
        User seller = saveUser("detail-seller", "detail-seller@example.com", "STUDENT");
        seller.setNickname("沙河数码同学");
        seller.setCampusRegion("沙河校区");
        seller.setCreditScore(98);
        seller.setPhone("13800000000");
        users.saveAndFlush(seller);
        User buyer = saveUser("detail-buyer", "detail-buyer@example.com", "STUDENT");
        var main = sellerInventory.publish(seller.getId(), new SellerInventory.ItemDraft(
            "九成新耳机", "数码电子", java.math.BigDecimal.valueOf(68), "功能正常，无拆修", "",
            "沙河校区", Set.of("可小刀", "支持验货")));
        sellerInventory.publish(seller.getId(), new SellerInventory.ItemDraft(
            "桌面支架", "数码电子", java.math.BigDecimal.valueOf(12), "配套支架", "",
            "沙河校区", Set.of("仅自提")));

        mvc.perform(get("/items/{id}", main.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("九成新耳机"))
            .andExpect(jsonPath("$.data.seller.displayName").value("沙河数码同学"))
            .andExpect(jsonPath("$.data.seller.region").value("沙河校区"))
            .andExpect(jsonPath("$.data.seller.creditScore").value(98))
            .andExpect(jsonPath("$.data.seller.onSaleCount").value(2))
            .andExpect(jsonPath("$.data.seller.email").doesNotExist())
            .andExpect(jsonPath("$.data.seller.phone").doesNotExist())
            .andExpect(jsonPath("$.data.viewer.authenticated").value(false))
            .andExpect(jsonPath("$.data.viewer.availableActions[0]").value("CHAT_SELLER"))
            .andExpect(jsonPath("$.data.viewer.availableActions[1]").value("REQUEST_PURCHASE"))
            .andExpect(jsonPath("$.data.sellerItems", hasSize(1)));

        tradingService.requestPurchase(buyer.getId(), main.id());
        MockCookie buyerSession = login(buyer.getEmail());
        mvc.perform(get("/items/{id}", main.id()).cookie(buyerSession))
            .andExpect(jsonPath("$.data.viewer.authenticated").value(true))
            .andExpect(jsonPath("$.data.viewer.owner").value(false))
            .andExpect(jsonPath("$.data.viewer.purchaseRequest.status").value("PURCHASE_REQUESTED"))
            .andExpect(jsonPath("$.data.viewer.availableActions", org.hamcrest.Matchers.hasItem("VIEW_PURCHASE_REQUEST")))
            .andExpect(jsonPath("$.data.viewer.availableActions", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("REQUEST_PURCHASE"))));

        MockCookie sellerSession = login(seller.getEmail());
        mvc.perform(get("/items/{id}", main.id()).cookie(sellerSession))
            .andExpect(jsonPath("$.data.viewer.owner").value(true))
            .andExpect(jsonPath("$.data.viewer.availableActions[0]").value("MANAGE_LISTING"))
            .andExpect(jsonPath("$.data.viewer.purchaseRequest").doesNotExist());
    }

    @Test
    void withdrawnItemPublicQuestionsAreHiddenAndRejectNewWrites() throws Exception {
        User seller = saveUser("question-seller", "question-seller@example.com", "STUDENT");
        User buyer = saveUser("question-buyer", "question-buyer@example.com", "STUDENT");
        var item = sellerInventory.publish(seller.getId(), new SellerInventory.ItemDraft(
            "问答测试商品", "其他", java.math.BigDecimal.TEN, "公开描述", ""));
        var message = new com.campus.secondhand.message.Message();
        message.setItemId(item.id());
        message.setSenderId(buyer.getId());
        message.setReceiverId(seller.getId());
        message.setContent("原有公开问题");
        messages.saveAndFlush(message);
        MockCookie sellerSession = login(seller.getEmail());
        MockCookie buyerSession = login(buyer.getEmail());

        mvc.perform(get("/messages/item/{id}", item.id()))
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data", hasSize(1)));
        sellerInventory.act(seller.getId(), item.id(), SellerItemAction.WITHDRAW);
        mvc.perform(get("/messages/item/{id}", item.id()))
            .andExpect(jsonPath("$.success").value(false));
        mvc.perform(get("/messages/item/{id}", item.id()).cookie(sellerSession))
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data", hasSize(1)));
        mvc.perform(post("/messages").cookie(buyerSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\":%d,\"content\":\"不应写入\"}".formatted(item.id())))
            .andExpect(jsonPath("$.success").value(false));
        org.junit.jupiter.api.Assertions.assertEquals(1, messages.count());
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
