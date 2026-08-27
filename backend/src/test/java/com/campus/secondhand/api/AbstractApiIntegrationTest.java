package com.campus.secondhand.api;

import com.campus.secondhand.chat.ChatBlockRepository;
import com.campus.secondhand.chat.ChatConversationRepository;
import com.campus.secondhand.chat.ChatMessageRepository;
import com.campus.secondhand.item.ItemRepository;
import com.campus.secondhand.item.SellerInventory;
import com.campus.secondhand.message.MessageRepository;
import com.campus.secondhand.order.TradeOrderRepository;
import com.campus.secondhand.report.ContentReportRepository;
import com.campus.secondhand.report.ReportActionRepository;
import com.campus.secondhand.user.EmailVerificationRepository;
import com.campus.secondhand.user.User;
import com.campus.secondhand.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class AbstractApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ItemRepository items;
    @Autowired SellerInventory sellerInventory;
    @Autowired MessageRepository messages;
    @Autowired TradeOrderRepository orders;
    @Autowired EmailVerificationRepository verifications;
    @Autowired ContentReportRepository contentReports;
    @Autowired ReportActionRepository reportActions;
    @Autowired ChatConversationRepository chatConversations;
    @Autowired ChatMessageRepository chatMessages;
    @Autowired ChatBlockRepository chatBlocks;
    @Autowired PasswordEncoder passwords;
    @MockitoBean JavaMailSender mailSender;

    @BeforeEach
    void cleanDatabase() {
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

    protected User saveUser(String name, String email, String role) {
        User user = new User();
        user.setUsername(name + UUID.randomUUID().toString().substring(0, 6));
        user.setEmail(email);
        user.setNickname(name);
        user.setRole(role);
        user.setPasswordHash(passwords.encode("abc123"));
        return users.saveAndFlush(user);
    }

    protected User saveUser(String name, String role) {
        return saveUser(name, name + "@example.com", role);
    }

    protected User createUser(String name, String email, String role) {
        return saveUser(name, email, role);
    }

    protected MockCookie login(String email) throws Exception {
        MvcResult result = mvc.perform(post("/auth/login").with(csrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"abc123\"}".formatted(email)))
            .andExpect(status().isOk()).andReturn();
        return (MockCookie) result.getResponse().getCookie("SESSION");
    }

    protected MockCookie login(User user) throws Exception {
        return login(user.getEmail());
    }
}
