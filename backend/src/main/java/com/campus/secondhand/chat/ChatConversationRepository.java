package com.campus.secondhand.chat;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ChatConversationRepository extends JpaRepository<ChatConversation,Long>{
    Optional<ChatConversation> findByItemIdAndBuyerIdAndSellerId(Long itemId,Long buyerId,Long sellerId);
    Optional<ChatConversation> findByPublicId(String publicId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from ChatConversation c where c.publicId=:publicId")
    Optional<ChatConversation> findLockedByPublicId(@Param("publicId") String publicId);
    @Query("select c from ChatConversation c where c.buyerId=:actor or c.sellerId=:actor order by coalesce(c.lastMessageAt,c.createdAt) desc,c.id desc")
    Page<ChatConversation> findForActor(@Param("actor") Long actor, Pageable pageable);
    @Query("select c from ChatConversation c where c.buyerId=:actor or c.sellerId=:actor")
    List<ChatConversation> findAllForActor(@Param("actor") Long actor);
}
