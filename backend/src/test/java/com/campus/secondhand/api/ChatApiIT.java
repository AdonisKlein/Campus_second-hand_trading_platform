package com.campus.secondhand.api;

import com.campus.secondhand.item.SellerInventory;
import com.campus.secondhand.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatApiIT extends AbstractApiIntegrationTest {
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void reset() { cleanDatabase(); }

    @Test
    void participantsCanReadSendTrackUnreadAndBlockEachOther() throws Exception {
        User seller = saveUser("chat-seller", "STUDENT");
        User buyer = saveUser("chat-buyer", "STUDENT");
        User stranger = saveUser("chat-stranger", "STUDENT");
        User admin = saveUser("chat-admin", "ADMIN");
        var item = sellerInventory.publish(seller.getId(), new SellerInventory.ItemDraft(
            "私聊测试教材", "书籍", BigDecimal.valueOf(18), "仅公开商品信息", ""));
        MockCookie buyerSession = login(buyer);
        MockCookie sellerSession = login(seller);
        MockCookie strangerSession = login(stranger);
        MockCookie adminSession = login(admin);

        MvcResult opened = mvc.perform(post("/chat/conversations").cookie(buyerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\":%d}".formatted(item.id())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.otherUserId").value(seller.getId())).andReturn();
        String conversationId = objectMapper.readTree(opened.getResponse().getContentAsString()).at("/data/id").asText();
        assertTrue(conversationId.matches("[0-9a-f-]{36}"));

        mvc.perform(post("/chat/conversations").cookie(buyerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"itemId\":%d}".formatted(item.id())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(conversationId));
        mvc.perform(get("/chat/conversations/{id}/messages", conversationId).cookie(strangerSession))
            .andExpect(status().isForbidden());
        mvc.perform(get("/chat/conversations").cookie(adminSession)).andExpect(status().isForbidden());

        mvc.perform(post("/chat/conversations/{id}/messages", conversationId).cookie(buyerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"这条只应由买卖双方看到\",\"senderId\":999}"))
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
            .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(delete("/chat/blocks/{id}", buyer.getId()).cookie(sellerSession).with(csrf()))
            .andExpect(status().isOk());
        mvc.perform(post("/chat/conversations/{id}/messages", conversationId).cookie(sellerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"解除后可以回复\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.sequence").value(2));
        mvc.perform(get("/messages/item/{id}", item.id()))
            .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.not(containsString("这条只应由买卖双方看到"))));
    }

    @Test
    void strangerCannotSendReadOrCreateTradeChat() throws Exception {
        User seller = saveUser("chat-seller", "STUDENT");
        User buyer = saveUser("chat-buyer", "STUDENT");
        User stranger = saveUser("chat-stranger", "STUDENT");
        var item = sellerInventory.publish(seller.getId(), new SellerInventory.ItemDraft(
            "越权测试教材", "书籍", BigDecimal.TEN, "", ""));
        MockCookie buyerSession = login(buyer);
        MockCookie strangerSession = login(stranger);
        MvcResult opened = mvc.perform(post("/chat/conversations").cookie(buyerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"itemId\":%d}".formatted(item.id())))
            .andExpect(status().isOk()).andReturn();
        String id = objectMapper.readTree(opened.getResponse().getContentAsString()).at("/data/id").asText();
        mvc.perform(post("/chat/conversations/{id}/messages", id).cookie(strangerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"越权\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/chat/conversations/{id}/read", id).cookie(strangerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"throughSequence\":1}"))
            .andExpect(status().isForbidden());
    }
}
