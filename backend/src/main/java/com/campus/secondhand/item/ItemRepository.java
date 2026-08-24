package com.campus.secondhand.item;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemRepository extends JpaRepository<Item, Long> {

    List<Item> findAllByOrderByCreatedAtDesc();

    List<Item> findBySellerIdOrderByCreatedAtDesc(Long sellerId);

    long countBySellerIdAndStatusAndModerationStatus(Long sellerId, ItemStatus status,
                                                     ItemModerationStatus moderationStatus);

    List<Item> findTop4BySellerIdAndStatusAndModerationStatusAndIdNotOrderByCreatedAtDesc(
        Long sellerId, ItemStatus status, ItemModerationStatus moderationStatus, Long excludedId);

    @Query("""
        select item from Item item
        where item.status = :status
          and item.moderationStatus = :moderationStatus
          and (:category is null or item.category = :category)
          and (:keyword is null or lower(item.title) like lower(concat('%', :keyword, '%')))
        order by item.createdAt desc
        """)
    List<Item> searchPublic(@Param("category") String category,
                            @Param("keyword") String keyword,
                            @Param("status") ItemStatus status,
                            @Param("moderationStatus") ItemModerationStatus moderationStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Item> findLockedById(Long id);
}
