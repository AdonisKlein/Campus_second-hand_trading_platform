package com.campus.secondhand.unit.order;

import com.campus.secondhand.item.Item;
import com.campus.secondhand.item.ItemRepository;
import com.campus.secondhand.order.TradingService;
import com.campus.secondhand.order.TradingRuleException;
import com.campus.secondhand.user.User;
import com.campus.secondhand.user.UserRepository;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradingServiceTest {
    @Mock com.campus.secondhand.order.TradeOrderRepository orders;
    @Mock ItemRepository items;
    @Mock UserRepository users;

    @Test void buyerCannotPurchaseOwnItem() {
        User seller = user(7L);
        Item item = new Item(); item.setId(20L); item.setSellerId(7L);
        when(users.findById(7L)).thenReturn(Optional.of(seller));
        when(items.findLockedById(20L)).thenReturn(Optional.of(item));
        TradingService service = new TradingService(orders, items, users, Clock.systemUTC(), 1440, 4320);
        assertThrows(TradingRuleException.class, () -> service.requestPurchase(7L, 20L));
    }

    @Test void unknownBuyerIsRejectedBeforeItemMutation() {
        when(users.findById(9L)).thenReturn(Optional.empty());
        TradingService service = new TradingService(orders, items, users, Clock.systemUTC(), 1440, 4320);
        assertThrows(TradingRuleException.class, () -> service.requestPurchase(9L, 20L));
    }

    private User user(Long id) { User u = new User(); u.setId(id); u.setRole("STUDENT"); u.setStatus("ACTIVE"); u.setUsername("u" + id); return u; }
}
