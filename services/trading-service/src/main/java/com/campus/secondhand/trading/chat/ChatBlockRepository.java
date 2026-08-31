package com.campus.secondhand.trading.chat;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ChatBlockRepository extends JpaRepository<ChatBlock,Long>{boolean existsByBlockerIdAndBlockedId(Long blocker,Long blocked);default boolean either(Long a,Long b){return existsByBlockerIdAndBlockedId(a,b)||existsByBlockerIdAndBlockedId(b,a);}void deleteByBlockerIdAndBlockedId(Long blocker,Long blocked);}
