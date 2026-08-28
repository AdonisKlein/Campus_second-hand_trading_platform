package com.campus.secondhand.trading.chat;
import java.util.Optional;
public interface OrderConversationPort { Optional<Participants> participants(Long orderId); record Participants(Long orderId,Long itemId,Long buyerId,Long sellerId,String itemTitle,String itemImageUrl,String buyerNickname,String sellerNickname){} }
