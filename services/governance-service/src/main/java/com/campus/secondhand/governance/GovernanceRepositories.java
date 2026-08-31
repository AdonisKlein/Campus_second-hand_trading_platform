package com.campus.secondhand.governance;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface ContentReportRepository extends JpaRepository<ContentReport,Long> {
    boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, ReportTargetType targetType, Long targetId);
    long countByReporterIdAndCreatedAtAfter(Long reporterId, LocalDateTime after);
    Page<ContentReport> findByReporterIdOrderByCreatedAtDesc(Long reporterId, Pageable pageable);
    Page<ContentReport> findByStatusOrderByCreatedAtDesc(ReportStatus status, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from ContentReport r where r.id=:id")
    Optional<ContentReport> findLockedById(@Param("id") Long id);
}
interface ReportActionRepository extends JpaRepository<ReportAction,Long> {
    List<ReportAction> findByReportIdInOrderByCreatedAtAsc(List<Long> reportIds);
}
interface GovernanceOutboxRepository extends JpaRepository<GovernanceOutboxEvent,Long> {
    List<GovernanceOutboxEvent> findByPublishedAtIsNullOrderById(Pageable pageable);
}
interface GovernanceInboxRepository extends JpaRepository<GovernanceInboxEvent,String> {}
