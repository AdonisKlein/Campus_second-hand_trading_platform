package com.campus.secondhand.report;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ReportActionRepository extends JpaRepository<ReportAction, Long> {
    List<ReportAction> findByReportIdOrderByCreatedAtAsc(Long reportId);
    List<ReportAction> findByReportIdInOrderByCreatedAtAsc(List<Long> reportIds);
}
