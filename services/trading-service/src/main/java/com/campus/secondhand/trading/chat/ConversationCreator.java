package com.campus.secondhand.trading.chat;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class ConversationCreator {
    private final ChatConversationRepository conversations;
    ConversationCreator(ChatConversationRepository conversations){this.conversations=conversations;}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    ChatConversation create(Long itemId,Long buyerId,Long sellerId,String buyerName,String sellerName,String title,String image){
        ChatConversation c=new ChatConversation();c.setItemId(itemId);c.setBuyerId(buyerId);c.setSellerId(sellerId);c.setBuyerNickname(buyerName);c.setSellerNickname(sellerName);c.setItemTitleSnapshot(title);c.setItemImageSnapshot(image);return conversations.saveAndFlush(c);
    }
}
