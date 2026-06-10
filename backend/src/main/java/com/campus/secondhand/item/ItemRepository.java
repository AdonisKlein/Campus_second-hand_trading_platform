package com.campus.secondhand.item;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Long> {

    List<Item> findAllByOrderByCreatedAtDesc();

    List<Item> findByStatusOrderByCreatedAtDesc(String status);

    List<Item> findByCategoryAndStatusOrderByCreatedAtDesc(String category, String status);

    List<Item> findByTitleContainingAndStatusOrderByCreatedAtDesc(String keyword, String status);
}
