package com.campus.secondhand.item;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {

    List<Item> findAllByOrderByCreatedAtDesc();

    List<Item> findByStatusOrderByCreatedAtDesc(String status);

    List<Item> findByCategoryAndStatusOrderByCreatedAtDesc(String category, String status);

    List<Item> findByTitleContainingAndStatusOrderByCreatedAtDesc(String keyword, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Item> findLockedById(Long id);
}
