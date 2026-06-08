package com.campus.secondhand;

import com.campus.secondhand.item.Item;
import com.campus.secondhand.item.ItemRepository;
import com.campus.secondhand.message.MessageRepository;
import com.campus.secondhand.order.TradeOrderRepository;
import com.campus.secondhand.user.EmailVerification;
import com.campus.secondhand.user.EmailVerificationRepository;
import com.campus.secondhand.user.User;
import com.campus.secondhand.user.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@ActiveProfiles("test")
@SpringBootTest
class SecondhandApplicationTests {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private TradeOrderRepository orderRepository;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @BeforeEach
    void cleanDatabase() {
        orderRepository.deleteAll();
        messageRepository.deleteAll();
        itemRepository.deleteAll();
        userRepository.deleteAll();
        emailVerificationRepository.deleteAll();
    }

    @Test
    void contextLoads() {
    }

    @Test
    void registerLoginAndRejectWrongPassword() throws Exception {
        saveVerificationCode("test@example.com", "123456");

        mockMvc.perform(post("/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "username": "testuser",
                        "password": "abc123",
                        "nickname": "测试用户",
                        "email": "test@example.com",
                        "code": "123456"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.username").value("testuser"));

        mockMvc.perform(post("/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "username": "testuser",
                        "password": "abc123"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "username": "testuser",
                        "password": "wrong123"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("用户名或密码错误，剩余尝试次数：2"));
    }

    @Test
    void lockUserAfterThreeFailedLogins() throws Exception {
        saveUser("lockuser");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                            "username": "lockuser",
                            "password": "wrong123"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
        }

        mockMvc.perform(post("/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "username": "lockuser",
                        "password": "wrong123"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("密码错误次数过多，账号已锁定10分钟"));

        mockMvc.perform(post("/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "username": "lockuser",
                        "password": "abc123"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("账号已被临时锁定，请10分钟后再试"));
    }

    @Test
    void resetPasswordWithVerificationCodeAndLoginWithNewPassword() throws Exception {
        User user = saveUser("resetuser");
        user.setEmail("reset@example.com");
        userRepository.save(user);
        saveVerificationCode("reset@example.com", "654321");

        mockMvc.perform(post("/users/forgot-password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "email": "reset@example.com",
                        "code": "654321",
                        "newPassword": "new123"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").value("密码重置成功"));

        mockMvc.perform(post("/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "username": "resetuser",
                        "password": "abc123"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "username": "resetuser",
                        "password": "new123"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.username").value("resetuser"));
    }

    @Test
    void publishAndSearchItems() throws Exception {
        User seller = saveUser("seller");

        mockMvc.perform(post("/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "title": "高等数学教材",
                        "category": "书籍",
                        "price": 18,
                        "description": "八成新",
                        "imageUrl": "assets/images/book.svg",
                        "sellerId": %d
                    }
                    """.formatted(seller.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.title").value("高等数学教材"));

        mockMvc.perform(get("/items").param("keyword", "数学"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].category").value("书籍"));
    }

    @Test
    void sendAndListMessages() throws Exception {
        User seller = saveUser("seller");
        User buyer = saveUser("buyer");
        Item item = saveItem(seller.getId());

        mockMvc.perform(post("/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "itemId": %d,
                        "senderId": %d,
                        "receiverId": %d,
                        "content": "这个还能便宜吗？"
                    }
                    """.formatted(item.getId(), buyer.getId(), seller.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content").value("这个还能便宜吗？"));

        mockMvc.perform(get("/messages/item/{itemId}", item.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void createOrderMarksItemSoldAndValidatesStatus() throws Exception {
        User seller = saveUser("seller");
        User buyer = saveUser("buyer");
        Item item = saveItem(seller.getId());

        mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "itemId": %d,
                        "buyerId": %d,
                        "sellerId": %d
                    }
                    """.formatted(item.getId(), buyer.getId(), seller.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("CREATED"));

        Long orderId = orderRepository.findAll().get(0).getId();

        mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "itemId": %d,
                        "buyerId": %d,
                        "sellerId": %d
                    }
                    """.formatted(item.getId(), buyer.getId(), seller.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("物品已被下单或售出"));

        mockMvc.perform(put("/orders/{id}/status", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "status": "CONFIRMED"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        mockMvc.perform(put("/orders/{id}/status", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "status": "WRONG"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("订单状态不合法"));

        mockMvc.perform(get("/items/{id}", item.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SOLD"));
    }

    private User saveUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("abc123"));
        user.setNickname(username);
        user.setPhone("13800000000");
        return userRepository.save(user);
    }

    private Item saveItem(Long sellerId) {
        Item item = new Item();
        item.setTitle("二手教材");
        item.setCategory("书籍");
        item.setPrice(BigDecimal.valueOf(20));
        item.setDescription("测试商品");
        item.setImageUrl("assets/images/book.svg");
        item.setSellerId(sellerId);
        return itemRepository.save(item);
    }

    private void saveVerificationCode(String email, String code) {
        EmailVerification verification = new EmailVerification();
        verification.setEmail(email);
        verification.setCode(code);
        verification.setCreatedAt(LocalDateTime.now());
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        verification.setAttempts(0);
        verification.setUsed(false);
        emailVerificationRepository.save(verification);
    }
}
