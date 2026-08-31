package com.campus.secondhand.marketplace.message;

import com.campus.secondhand.marketplace.*;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicQuestionsService implements PublicQuestions {
    private final MessageRepository messages;
    private final ProductDetail details;
    public PublicQuestionsService(MessageRepository messages, ProductDetail details) { this.messages=messages; this.details=details; }
    @Override @Transactional(readOnly=true)
    public List<Message> list(Long itemId) {
        if (details.show(itemId, null).isEmpty()) throw new MarketplaceException(org.springframework.http.HttpStatus.NOT_FOUND,"ITEM_NOT_FOUND","商品不存在");
        return messages.findByItemIdOrderByCreatedAtAsc(itemId);
    }
    @Override @Transactional
    public Message ask(Long actorId, Long itemId, Long ignoredSellerId, String content) {
        ProductDetail.View detail=details.show(itemId, null).orElseThrow(() -> new MarketplaceException(org.springframework.http.HttpStatus.NOT_FOUND,"ITEM_NOT_FOUND","商品不存在"));
        if(detail.status()!=ItemStatus.ON_SALE||detail.moderationStatus()!=ItemModerationStatus.VISIBLE) throw new MarketplaceException("ITEM_NOT_ASKABLE","商品当前不能发布公开问题");
        if(actorId.equals(detail.sellerId())) throw new MarketplaceException("SELF_QUESTION_NOT_ALLOWED","不能给自己发布的商品提问");
        Message message=new Message(); message.setItemId(itemId); message.setSenderId(actorId);
        message.setReceiverId(detail.sellerId()); message.setContent(content.trim()); return messages.save(message);
    }
    @Override @Transactional
    public Message edit(Long actorId,Long id,String content){Message value=own(actorId,id);value.setContent(content.trim());return value;}
    @Override @Transactional
    public void delete(Long actorId,Long id,boolean admin){Message value=messages.findById(id).orElseThrow(()->new MarketplaceException(org.springframework.http.HttpStatus.NOT_FOUND,"MESSAGE_NOT_FOUND","留言不存在"));if(!admin&&!actorId.equals(value.getSenderId()))throw new AccessDeniedException("只能删除自己的留言");messages.delete(value);}
    private Message own(Long actorId,Long id){Message value=messages.findById(id).orElseThrow(()->new MarketplaceException(org.springframework.http.HttpStatus.NOT_FOUND,"MESSAGE_NOT_FOUND","留言不存在"));if(!actorId.equals(value.getSenderId()))throw new AccessDeniedException("只能修改自己的留言");return value;}
}
