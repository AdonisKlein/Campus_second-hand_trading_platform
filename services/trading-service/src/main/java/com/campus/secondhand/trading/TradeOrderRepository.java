package com.campus.secondhand.trading;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TradeOrderRepository extends JpaRepository<TradeOrder,Long>{
    List<TradeOrder> findByBuyerIdOrSellerIdOrderByCreatedAtDesc(Long buyerId,Long sellerId);
    List<TradeOrder> findByBuyerIdOrderByCreatedAtDesc(Long buyerId); List<TradeOrder> findBySellerIdOrderByCreatedAtDesc(Long sellerId);
    List<TradeOrder> findByItemIdAndStatus(Long itemId,OrderStatus status);
    List<TradeOrder> findByStatusInAndExpiresAtBefore(List<OrderStatus> statuses,LocalDateTime cutoff);
    Optional<TradeOrder> findFirstByItemIdAndBuyerIdAndStatusIn(Long itemId,Long buyerId,List<OrderStatus> statuses);
    boolean existsByItemIdAndSagaState(Long itemId,SagaState sagaState);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from TradeOrder o where o.id=:id") Optional<TradeOrder> findLockedById(@Param("id")Long id);
}
