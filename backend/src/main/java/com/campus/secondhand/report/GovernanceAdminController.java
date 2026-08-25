package com.campus.secondhand.report;

import com.campus.secondhand.common.ApiResponse;
import com.campus.secondhand.security.CurrentActorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/reports")
public class GovernanceAdminController {
    private final ContentGovernance governance;
    private final CurrentActorService actors;
    public GovernanceAdminController(ContentGovernance governance, CurrentActorService actors) { this.governance = governance; this.actors = actors; }

    @ModelAttribute public void requireAdmin() { if (!actors.require().isAdmin()) throw new AccessDeniedException("无管理员权限"); }

    @GetMapping
    public ApiResponse<ContentGovernance.ReportPage> list(@RequestParam(required = false) ReportStatus status,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.ok(governance.listForAdmin(status, page, size));
    }

    @PutMapping("/{id}")
    public ApiResponse<ContentGovernance.ReportView> decide(@PathVariable Long id,
                                                            @Valid @RequestBody DecisionRequest request) {
        return ApiResponse.ok(governance.decide(actors.require().userId(), id,
            new ContentGovernance.Decision(request.status(), request.action(), request.note())));
    }

    public record DecisionRequest(@NotNull ReportStatus status, GovernanceAction action,
                                  @NotBlank @Size(min = 2, max = 1000) String note) {}
}
