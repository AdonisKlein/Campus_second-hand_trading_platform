package com.campus.secondhand.chat;

import com.campus.secondhand.item.Item;
import com.campus.secondhand.item.ItemModerationStatus;
import com.campus.secondhand.item.ItemRepository;
import com.campus.secondhand.item.ItemStatus;
import com.campus.secondhand.order.TradeOrderRepository;
import com.campus.secondhand.user.User;
import com.campus.secondhand.user.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DirectChatService implements DirectChat {
    private static final int MAX_CONVERSATIONS = 50;
    private static final int MAX_MESSAGES = 100;
    private static final int SENDS_PER_MINUTE = 30;

    private final ChatConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final ChatBlockRepository blocks;
    private final ItemRepository items;
    private final UserRepository users;
    private final TradeOrderRepository orders;

    public DirectChatService(ChatConversationRepository conversations, ChatMessageRepository messages,
                             ChatBlockRepository blocks, ItemRepository items, UserRepository users,
                             TradeOrderRepository orders) {
        this.conversations = conversations;
        this.messages = messages;
        this.blocks = blocks;
        this.items = items;
        this.users = users;
        this.orders = orders;
    }

    @Override @Transactional
    public ConversationView open(Long actorId, Long itemId) {
        User buyer = requireStudent(actorId);
        Item item = items.findLockedById(itemId).orElseThrow(() -> new ChatRuleException("商品不存在"));
        if (actorId.equals(item.getSellerId())) throw new ChatRuleException("不能和自己发起私聊");
        User seller = requireStudent(item.getSellerId());
        var existing = conversations.findByItemIdAndBuyerIdAndSellerId(itemId, buyer.getId(), seller.getId());
        if (existing.isPresent()) return view(existing.get(), actorId);
        boolean tradeParticipant = orders.existsByItemIdAndBuyerIdAndSellerId(itemId, buyer.getId(), seller.getId());
        if ((item.getStatus() != ItemStatus.ON_SALE || item.getModerationStatus() != ItemModerationStatus.VISIBLE)
                && !tradeParticipant) {
            throw new ChatRuleException("该商品当前不能发起私聊");
        }
        ChatConversation conversation = conversations.save(newConversation(item, buyer, seller));
        return view(conversation, actorId);
    }

    @Override @Transactional
    public ConversationView openTrade(Long actorId, Long orderId) {
        requireStudent(actorId);
        var order = orders.findById(orderId).orElseThrow(() -> new ChatRuleException("交易记录不存在"));
        if (!actorId.equals(order.getBuyerId()) && !actorId.equals(order.getSellerId())) {
            throw new AccessDeniedException("order conversation");
        }
        User buyer = requireStudent(order.getBuyerId());
        User seller = requireStudent(order.getSellerId());
        var existing = conversations.findByItemIdAndBuyerIdAndSellerId(order.getItemId(), buyer.getId(), seller.getId());
        if (existing.isPresent()) return view(existing.get(), actorId);
        Item item = items.findById(order.getItemId()).orElseThrow(() -> new ChatRuleException("交易商品不存在"));
        return view(conversations.save(newConversation(item, buyer, seller)), actorId);
    }

    private ChatConversation newConversation(Item item, User buyer, User seller) {
                ChatConversation created = new ChatConversation();
                created.setItemId(item.getId()); created.setBuyerId(buyer.getId()); created.setSellerId(seller.getId());
                created.setItemTitleSnapshot(item.getTitle()); created.setItemImageSnapshot(item.getImageUrl());
                return created;
    }

    @Override @Transactional(readOnly = true)
    public ConversationPage conversations(Long actorId, int page, int size) {
        requireStudent(actorId);
        int safePage = Math.max(0, page), safeSize = Math.max(1, Math.min(size, MAX_CONVERSATIONS));
        Page<ChatConversation> result = conversations.findForActor(actorId, PageRequest.of(safePage, safeSize));
        return new ConversationPage(result.getContent().stream().map(c -> view(c, actorId)).toList(),
            safePage, safeSize, result.hasNext(), unreadTotalUnchecked(actorId));
    }

    @Override @Transactional(readOnly = true)
    public MessagePage history(Long actorId, String conversationId, Long beforeSequence, int size) {
        requireStudent(actorId);
        ChatConversation conversation = participant(conversationId, actorId, false);
        int safeSize = Math.max(1, Math.min(size, MAX_MESSAGES));
        long before = beforeSequence == null ? Long.MAX_VALUE : Math.max(1, beforeSequence);
        List<ChatMessage> found = new ArrayList<>(messages.findPage(conversation.getId(), before, PageRequest.of(0, safeSize + 1)));
        boolean hasMore = found.size() > safeSize;
        if (hasMore) found.remove(found.size() - 1);
        Long next = hasMore && !found.isEmpty() ? found.get(found.size() - 1).getSequenceNumber() : null;
        Collections.reverse(found);
        return new MessagePage(view(conversation, actorId), found.stream().map(this::view).toList(), next, hasMore);
    }

    @Override @Transactional
    public MessageView send(Long actorId, String conversationId, String body) {
        requireStudent(actorId);
        String normalized = body == null ? "" : body.trim();
        if (normalized.isEmpty() || normalized.length() > 2000) throw new ChatRuleException("消息内容应为 1 到 2000 个字符");
        ChatConversation conversation = participant(conversationId, actorId, true);
        Long otherId = other(conversation, actorId);
        requireStudent(otherId);
        if (blocks.existsEitherDirection(actorId, otherId)) throw new ChatRuleException("当前无法向对方发送消息");
        long recent = messages.countBySenderIdAndCreatedAtAfter(actorId, LocalDateTime.now().minusMinutes(1));
        if (recent >= SENDS_PER_MINUTE) throw new ChatRuleException("发送过于频繁，请稍后再试");
        long sequence = conversation.getNextSequence();
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversation.getId()); message.setSenderId(actorId); message.setSequenceNumber(sequence); message.setBody(normalized);
        messages.save(message);
        conversation.setNextSequence(sequence + 1);
        conversation.setLastMessageAt(message.getCreatedAt());
        conversation.setLastMessagePreview(normalized.length() <= 160 ? normalized : normalized.substring(0, 160));
        if (actorId.equals(conversation.getBuyerId())) conversation.setBuyerLastReadSequence(sequence);
        else conversation.setSellerLastReadSequence(sequence);
        return view(message);
    }

    @Override @Transactional
    public ConversationView markRead(Long actorId, String conversationId, Long throughSequence) {
        requireStudent(actorId);
        ChatConversation conversation = participant(conversationId, actorId, true);
        long through = throughSequence == null ? conversation.getNextSequence() - 1 : throughSequence;
        through = Math.max(0, Math.min(through, conversation.getNextSequence() - 1));
        if (actorId.equals(conversation.getBuyerId())) conversation.setBuyerLastReadSequence(Math.max(conversation.getBuyerLastReadSequence(), through));
        else conversation.setSellerLastReadSequence(Math.max(conversation.getSellerLastReadSequence(), through));
        return view(conversation, actorId);
    }

    @Override @Transactional
    public void block(Long actorId, Long otherUserId) {
        requireStudent(actorId); requireStudent(otherUserId);
        if (actorId.equals(otherUserId)) throw new ChatRuleException("不能屏蔽自己");
        if (!blocks.existsByBlockerIdAndBlockedId(actorId, otherUserId)) {
            ChatBlock block = new ChatBlock(); block.setBlockerId(actorId); block.setBlockedId(otherUserId); blocks.save(block);
        }
    }

    @Override @Transactional
    public void unblock(Long actorId, Long otherUserId) {
        requireStudent(actorId);
        blocks.deleteByBlockerIdAndBlockedId(actorId, otherUserId);
    }

    @Override @Transactional(readOnly = true)
    public long unreadTotal(Long actorId) { requireStudent(actorId); return unreadTotalUnchecked(actorId); }

    private long unreadTotalUnchecked(Long actorId) {
        return conversations.findAllForActor(actorId).stream().mapToLong(c -> unread(c, actorId)).sum();
    }

    private ChatConversation participant(String publicId, Long actorId, boolean locked) {
        ChatConversation c = (locked ? conversations.findLockedByPublicId(publicId) : conversations.findByPublicId(publicId))
            .orElseThrow(() -> new AccessDeniedException("conversation"));
        if (!actorId.equals(c.getBuyerId()) && !actorId.equals(c.getSellerId())) throw new AccessDeniedException("conversation");
        return c;
    }

    private User requireStudent(Long id) {
        User user = users.findById(id).orElseThrow(() -> new AccessDeniedException("user"));
        if (!"ACTIVE".equals(user.getStatus()) || !"STUDENT".equals(user.getRole())) throw new AccessDeniedException("user");
        return user;
    }

    private Long other(ChatConversation c, Long actorId) { return actorId.equals(c.getBuyerId()) ? c.getSellerId() : c.getBuyerId(); }
    private long unread(ChatConversation c, Long actorId) {
        long read = actorId.equals(c.getBuyerId()) ? c.getBuyerLastReadSequence() : c.getSellerLastReadSequence();
        return Math.max(0, c.getNextSequence() - 1 - read);
    }
    private ConversationView view(ChatConversation c, Long actorId) {
        Long otherId = other(c, actorId);
        User other = users.findById(otherId).orElse(null);
        String name = other == null ? "已注销用户" : (other.getNickname() == null || other.getNickname().isBlank() ? other.getUsername() : other.getNickname());
        boolean byMe = blocks.existsByBlockerIdAndBlockedId(actorId, otherId);
        return new ConversationView(c.getPublicId(), c.getItemId(), c.getItemTitleSnapshot(), c.getItemImageSnapshot(), otherId, name,
            c.getLastMessagePreview(), c.getLastMessageAt(), c.getNextSequence() - 1, unread(c, actorId), byMe, blocks.existsEitherDirection(actorId, otherId));
    }
    private MessageView view(ChatMessage m) { return new MessageView(m.getSequenceNumber(), m.getSenderId(), m.getBody(), m.getCreatedAt()); }
}
