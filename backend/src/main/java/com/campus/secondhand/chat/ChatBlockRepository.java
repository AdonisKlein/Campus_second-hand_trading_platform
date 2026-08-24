package com.campus.secondhand.chat;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface ChatBlockRepository extends JpaRepository<ChatBlock,Long>{
    boolean existsByBlockerIdAndBlockedId(Long blockerId,Long blockedId);
    @Query("select (count(b)>0) from ChatBlock b where (b.blockerId=:a and b.blockedId=:b) or (b.blockerId=:b and b.blockedId=:a)")
    boolean existsEitherDirection(@Param("a") Long a,@Param("b") Long b);
    List<ChatBlock> findByBlockerId(Long blockerId);
    void deleteByBlockerIdAndBlockedId(Long blockerId,Long blockedId);
}
