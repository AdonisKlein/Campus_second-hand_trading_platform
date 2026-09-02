package com.campus.secondhand.marketplace.message;

import java.util.List;

public interface PublicQuestions {
    List<Message> list(Long itemId);
    Message ask(Long actorId, Long itemId, Long sellerId, Long replyToId, String content);
    Message edit(Long actorId, Long id, String content);
    void delete(Long actorId, Long id, boolean admin);
}
