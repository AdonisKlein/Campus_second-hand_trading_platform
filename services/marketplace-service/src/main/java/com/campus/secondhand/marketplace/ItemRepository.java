package com.campus.secondhand.marketplace;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemRepository extends JpaRepository<Item, Long> {
    List<Item> findBySellerIdOrderByCreatedAtDesc(Long sellerId);

    long countBySellerIdAndStatusAndModerationStatus(
            Long sellerId, ItemStatus status, ItemModerationStatus moderationStatus);

    List<Item> findTop4BySellerIdAndStatusAndModerationStatusAndIdNotOrderByCreatedAtDesc(
            Long sellerId, ItemStatus status, ItemModerationStatus moderationStatus, Long excludedId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from Item item where item.id = :id")
    Optional<Item> findLockedById(@Param("id") Long id);

    @Query("""
        select item from Item item join item.sellerProjection seller
        where item.status = :status and item.moderationStatus = :moderation and seller.status = 'ACTIVE'
          and (:category is null or item.category = :category)
          and (:keyword is null or lower(item.title) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(item.description, '')) like lower(concat('%', :keyword, '%')))
        order by item.createdAt desc, item.id desc
        """)
    List<Item> searchPublic(@Param("category") String category, @Param("keyword") String keyword,
                            @Param("status") ItemStatus status,
                            @Param("moderation") ItemModerationStatus moderation);
}
