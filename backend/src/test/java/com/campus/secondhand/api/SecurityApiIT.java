package com.campus.secondhand.api;

import com.campus.secondhand.item.SellerInventory;
import com.campus.secondhand.user.User;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityApiIT extends AbstractApiIntegrationTest {
    @Test void protectedWritesRequireSessionAndCsrf() throws Exception {
        mvc.perform(post("/items").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"匿名\",\"category\":\"其他\",\"price\":1}"))
            .andExpect(status().isForbidden());
        User seller = createUser("security-seller", "security-seller@example.com", "STUDENT");
        MockCookie session = login(seller);
        mvc.perform(post("/items").cookie(session).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"无 CSRF\",\"category\":\"其他\",\"price\":1}"))
            .andExpect(status().isForbidden());
    }

    @Test void forgedOwnerFieldCannotTransferItemOwnership() throws Exception {
        User seller = createUser("security-owner", "security-owner@example.com", "STUDENT");
        User victim = createUser("security-victim", "security-victim@example.com", "STUDENT");
        MockCookie session = login(seller);
        var item = sellerInventory.publish(seller.getId(), new SellerInventory.ItemDraft("安全测试商品", "其他", BigDecimal.TEN, "", ""));
        mvc.perform(put("/items/{id}", item.id()).cookie(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"仍归卖家\",\"category\":\"其他\",\"price\":10,\"sellerId\":" + victim.getId() + "}"))
            .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(seller.getId(), items.findById(item.id()).orElseThrow().getSellerId());
    }
}
