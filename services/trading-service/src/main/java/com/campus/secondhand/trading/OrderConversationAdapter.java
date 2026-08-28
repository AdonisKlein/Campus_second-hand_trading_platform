package com.campus.secondhand.trading;

import java.util.Optional;
import org.springframework.stereotype.Component;
import com.campus.secondhand.trading.chat.OrderConversationPort;

@Component
class OrderConversationAdapter implements OrderConversationPort {
    private final TradeOrderRepository orders;
    OrderConversationAdapter(TradeOrderRepository orders){this.orders=orders;}
    @Override public Optional<Participants> participants(Long orderId){return orders.findById(orderId).map(o->new Participants(o.getId(),o.getItemId(),o.getBuyerId(),o.getSellerId(),o.getItemTitle(),o.getItemImageUrl(),o.getBuyerNickname(),o.getSellerNickname()));}
}
