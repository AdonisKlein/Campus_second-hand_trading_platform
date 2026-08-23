package com.campus.secondhand.item;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {

    List<Item> findAllByOrderByCreatedAtDesc();

    List<Item> findByStatusAndModerationStatusOrderByCreatedAtDesc(ItemStatus status, ItemModerationStatus moderationStatus);

    List<Item> findByCategoryAndStatusAndModerationStatusOrderByCreatedAtDesc(
        String category, ItemStatus status, ItemModerationStatus moderationStatus);

    List<Item> findByTitleContainingAndStatusAndModerationStatusOrderByCreatedAtDesc(
        String keyword, ItemStatus status, ItemModerationStatus moderationStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Item> findLockedById(Long id);
}
