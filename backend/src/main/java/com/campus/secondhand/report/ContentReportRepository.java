package com.campus.secondhand.report;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ContentReportRepository extends JpaRepository<ContentReport, Long> {
    boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, ReportTargetType targetType, Long targetId);
    long countByReporterIdAndCreatedAtAfter(Long reporterId, LocalDateTime after);
    Page<ContentReport> findByReporterIdOrderByCreatedAtDesc(Long reporterId, Pageable pageable);
    Page<ContentReport> findByStatusOrderByCreatedAtDesc(ReportStatus status, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select report from ContentReport report where report.id = :id")
    Optional<ContentReport> findLockedById(@Param("id") Long id);
}
