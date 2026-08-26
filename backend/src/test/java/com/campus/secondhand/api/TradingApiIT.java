package com.campus.secondhand.api;

import com.campus.secondhand.item.ItemStatus;
import com.campus.secondhand.item.SellerInventory;
import com.campus.secondhand.order.OrderAction;
import com.campus.secondhand.order.OrderStatus;
import com.campus.secondhand.order.TradingRuleException;
import com.campus.secondhand.order.TradingService;
import com.campus.secondhand.user.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TradingApiIT extends AbstractApiIntegrationTest {
    @Autowired TradingService tradingService;
    @BeforeEach
    void reset() { cleanDatabase(); }

    @Test
    void buyerRequestsSellerAcceptsBuyerCompletesAndItemIsSold() throws Exception {
        User seller = saveUser("trade-seller", "STUDENT");
        User buyer = saveUser("trade-buyer", "STUDENT");
        User stranger = saveUser("trade-stranger", "STUDENT");
        var item = sellerInventory.publish(seller.getId(), new SellerInventory.ItemDraft(
            "订单流程教材", "书籍", BigDecimal.valueOf(20), "九成新", ""));
        MockCookie sellerSession = login(seller);
        MockCookie buyerSession = login(buyer);
        MockCookie strangerSession = login(stranger);

        mvc.perform(post("/orders").cookie(buyerSession).contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\":%d}".formatted(item.id())))
            .andExpect(status().isForbidden());
        mvc.perform(post("/orders").cookie(buyerSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\":%d,\"sellerId\":%d}".formatted(item.id(), seller.getId())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.buyerId").value(buyer.getId()))
            .andExpect(jsonPath("$.data.status").value("PURCHASE_REQUESTED"));
        Long orderId = orders.findAll().getFirst().getId();
        assertEquals(ItemStatus.ON_SALE, items.findById(item.id()).orElseThrow().getStatus());

        mvc.perform(post("/orders/{id}/actions", orderId).cookie(strangerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"))
            .andExpect(status().isConflict());
        mvc.perform(post("/orders/{id}/actions", orderId).cookie(sellerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("WAITING_HANDOVER"));
        assertEquals(ItemStatus.RESERVED, items.findById(item.id()).orElseThrow().getStatus());

        mvc.perform(post("/chat/order-conversations").cookie(strangerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":%d}".formatted(orderId)))
            .andExpect(status().isForbidden());
        mvc.perform(post("/orders/{id}/actions", orderId).cookie(buyerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"COMPLETE\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
        assertEquals(ItemStatus.SOLD, items.findById(item.id()).orElseThrow().getStatus());
        mvc.perform(get("/orders").cookie(buyerSession)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1))).andExpect(jsonPath("$.data[0].itemTitle").value("订单流程教材"))
            .andExpect(jsonPath("$.data[0].allowedActions", hasSize(0)));
    }

    @Test
    void sellerSelectionReservesOnlyAfterChoiceAndDeclinesOtherBuyer() {
        User seller = saveUser("choice-seller", "STUDENT");
        User buyerA = saveUser("choice-a", "STUDENT");
        User buyerB = saveUser("choice-b", "STUDENT");
        var item = sellerInventory.publish(seller.getId(), new SellerInventory.ItemDraft(
            "多人意向教材", "书籍", BigDecimal.valueOf(30), "", ""));
        var requestA = tradingService.requestPurchase(buyerA.getId(), item.id());
        var requestB = tradingService.requestPurchase(buyerB.getId(), item.id());
        assertEquals(OrderStatus.PURCHASE_REQUESTED, requestA.status());
        assertEquals(OrderStatus.PURCHASE_REQUESTED, requestB.status());
        assertEquals(ItemStatus.ON_SALE, items.findById(item.id()).orElseThrow().getStatus());
        assertThrows(TradingRuleException.class, () -> tradingService.requestPurchase(buyerA.getId(), item.id()));

        var accepted = tradingService.perform(seller.getId(), requestB.id(), OrderAction.ACCEPT);
        assertEquals(OrderStatus.WAITING_HANDOVER, accepted.status());
        assertEquals(ItemStatus.RESERVED, items.findById(item.id()).orElseThrow().getStatus());
        assertEquals(OrderStatus.DECLINED, orders.findById(requestA.id()).orElseThrow().getStatus());
        assertEquals("卖家已选择其他买家", orders.findById(requestA.id()).orElseThrow().getClosureReason());
    }

    @Test
    void expiredRequestReturnsItemToSaleAndCannotBeAcceptedByStranger() {
        User seller = saveUser("expired-seller", "STUDENT");
        User buyer = saveUser("expired-buyer", "STUDENT");
        User stranger = saveUser("expired-stranger", "STUDENT");
        var item = sellerInventory.publish(seller.getId(), new SellerInventory.ItemDraft(
            "过期意向教材", "书籍", BigDecimal.valueOf(20), "", ""));
        var request = tradingService.requestPurchase(buyer.getId(), item.id());
        var order = orders.findById(request.id()).orElseThrow();
        order.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        orders.saveAndFlush(order);
        assertThrows(TradingRuleException.class, () -> tradingService.perform(stranger.getId(), order.getId(), OrderAction.ACCEPT));
        assertEquals(OrderStatus.PURCHASE_REQUESTED, orders.findById(order.getId()).orElseThrow().getStatus());
        var expired = tradingService.perform(seller.getId(), order.getId(), OrderAction.ACCEPT);
        assertEquals(OrderStatus.EXPIRED, expired.status());
        assertEquals(ItemStatus.ON_SALE, items.findById(item.id()).orElseThrow().getStatus());
    }
}
