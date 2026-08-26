package com.campus.secondhand.api;

import com.campus.secondhand.item.SellerInventory;
import com.campus.secondhand.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;

import java.math.BigDecimal;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductApiIT extends AbstractApiIntegrationTest {
    @Test
    void publicSearchCombinesTermsAndFiltersSafely() throws Exception {
        User seller = createUser("product-search", "product-search@example.com", "STUDENT");
        sellerInventory.publish(seller.getId(), new SellerInventory.ItemDraft("Java 第七版教材", "书籍", BigDecimal.TEN, "", ""));
        mvc.perform(get("/search").param("scope", "ITEMS").param("q", "Java 第七版"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items", hasSize(1)))
            .andExpect(jsonPath("$.data.items[0].title").value("Java 第七版教材"));
        mvc.perform(get("/search").param("minPrice", "50").param("maxPrice", "20"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void publishUsesSessionIdentityAndRejectsAnonymous() throws Exception {
        User seller = createUser("product-seller", "product-seller@example.com", "STUDENT");
        User victim = createUser("product-victim", "product-victim@example.com", "STUDENT");
        mvc.perform(post("/items").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"匿名商品\",\"category\":\"其他\",\"price\":1}"))
            .andExpect(status().isUnauthorized());
        MockCookie session = login(seller.getEmail());
        mvc.perform(post("/items").cookie(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"安全教材\",\"category\":\"书籍\",\"price\":20,\"sellerId\":%d}".formatted(victim.getId())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.sellerId").value(seller.getId()));
        org.junit.jupiter.api.Assertions.assertEquals(seller.getId(), items.findAll().getFirst().getSellerId());
    }

    @Test
    void sellerCanListAndEditOwnItemButStrangerCannot() throws Exception {
        User seller = createUser("product-owner", "product-owner@example.com", "STUDENT");
        User stranger = createUser("product-stranger", "product-stranger@example.com", "STUDENT");
        MockCookie sellerSession = login(seller.getEmail());
        mvc.perform(post("/items").cookie(sellerSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"台灯\",\"category\":\"生活用品\",\"price\":25}"))
            .andExpect(status().isOk());
        Long itemId = items.findAll().getFirst().getId();
        mvc.perform(get("/items/mine").cookie(sellerSession)).andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(1)));
        mvc.perform(put("/items/{id}", itemId).cookie(login(stranger.getEmail())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"冒充修改\",\"category\":\"其他\",\"price\":1}"))
            .andExpect(status().isForbidden());
        mvc.perform(put("/items/{id}", itemId).cookie(sellerSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"修改后的台灯\",\"category\":\"生活用品\",\"price\":20}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("修改后的台灯"));
        org.junit.jupiter.api.Assertions.assertEquals("修改后的台灯", items.findById(itemId).orElseThrow().getTitle());
    }
}
