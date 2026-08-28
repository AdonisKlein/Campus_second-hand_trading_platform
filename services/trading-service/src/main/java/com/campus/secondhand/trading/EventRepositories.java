package com.campus.secondhand.trading;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface OutboxEventRepository extends JpaRepository<OutboxEvent,Long>{
    List<OutboxEvent> findByPublishedAtIsNullOrderById(Pageable pageable);
}
interface InboxEventRepository extends JpaRepository<InboxEvent,String>{}
