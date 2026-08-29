package com.campus.secondhand.unit.chat;

import com.campus.secondhand.chat.*;
import com.campus.secondhand.chat.ChatRuleException;
import org.springframework.security.access.AccessDeniedException;
import com.campus.secondhand.item.ItemRepository;
import com.campus.secondhand.order.TradeOrderRepository;
import com.campus.secondhand.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectChatServiceTest {
    @Mock ChatConversationRepository conversations;
    @Mock ChatMessageRepository messages;
    @Mock ChatBlockRepository blocks;
    @Mock ItemRepository items;
    @Mock UserRepository users;
    @Mock TradeOrderRepository orders;

    @Test void blankMessageIsRejectedBeforeConversationLookup() {
        when(users.findById(7L)).thenReturn(Optional.of(activeStudent(7L)));
        DirectChatService service = new DirectChatService(conversations, messages, blocks, items, users, orders);
        assertThrows(ChatRuleException.class, () -> service.send(7L, "missing", "   "));
    }

    @Test void inactiveActorCannotOpenChat() {
        var user = activeStudent(7L); user.setStatus("DISABLED");
        when(users.findById(7L)).thenReturn(Optional.of(user));
        DirectChatService service = new DirectChatService(conversations, messages, blocks, items, users, orders);
        assertThrows(AccessDeniedException.class, () -> service.open(7L, 20L));
    }

    private com.campus.secondhand.user.User activeStudent(Long id) {
        var user = new com.campus.secondhand.user.User(); user.setId(id); user.setRole("STUDENT"); user.setStatus("ACTIVE"); user.setUsername("u" + id); return user;
    }
}
