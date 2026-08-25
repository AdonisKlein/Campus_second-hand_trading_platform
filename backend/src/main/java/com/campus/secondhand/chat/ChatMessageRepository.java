package com.campus.secondhand.chat;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface ChatMessageRepository extends JpaRepository<ChatMessage,Long>{
    @Query("select m from ChatMessage m where m.conversationId=:conversationId and m.sequenceNumber<:before order by m.sequenceNumber desc")
    List<ChatMessage> findPage(@Param("conversationId") Long conversationId,@Param("before") Long before,Pageable pageable);
    long countBySenderIdAndCreatedAtAfter(Long senderId, LocalDateTime after);
}
