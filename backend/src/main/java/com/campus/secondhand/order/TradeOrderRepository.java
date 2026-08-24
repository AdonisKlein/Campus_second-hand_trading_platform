package com.campus.secondhand.order;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long> {
    List<TradeOrder> findByBuyerIdOrSellerIdOrderByCreatedAtDesc(Long buyerId, Long sellerId);
    Optional<TradeOrder> findFirstByItemIdAndBuyerIdAndStatusIn(Long itemId, Long buyerId, List<OrderStatus> statuses);
    List<TradeOrder> findByItemIdAndStatus(Long itemId, OrderStatus status);
    List<TradeOrder> findByStatusInAndExpiresAtBefore(List<OrderStatus> statuses, LocalDateTime cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TradeOrder> findLockedById(Long id);
}
