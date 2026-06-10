package com.campus.secondhand.message;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByItemIdOrderByCreatedAtAsc(Long itemId);

    List<Message> findAllByOrderByCreatedAtDesc();
}
