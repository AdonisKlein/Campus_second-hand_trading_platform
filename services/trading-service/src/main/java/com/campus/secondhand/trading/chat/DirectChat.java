package com.campus.secondhand.trading.chat;
import java.time.LocalDateTime; import java.util.List;
public interface DirectChat {
 ConversationView open(Long actorId,Long itemId); ConversationView openTrade(Long actorId,Long orderId);
 ConversationPage conversations(Long actorId,int page,int size); MessagePage history(Long actorId,String id,Long before,int size);
 MessageView send(Long actorId,String id,String body); ConversationView markRead(Long actorId,String id,Long through);
 void block(Long actorId,Long other); void unblock(Long actorId,Long other); long unreadTotal(Long actorId);
 record ConversationPage(List<ConversationView> conversations,int page,int size,boolean hasNext,long totalUnread){}
 record MessagePage(ConversationView conversation,List<MessageView> messages,Long nextBeforeSequence,boolean hasMore){}
 record ConversationView(String id,Long itemId,String itemTitle,String itemImageUrl,Long otherUserId,String otherNickname,String lastMessagePreview,LocalDateTime lastMessageAt,long lastSequence,long unreadCount,boolean blockedByMe,boolean blocked){}
 record MessageView(Long sequence,Long senderId,String body,LocalDateTime createdAt){}
}
