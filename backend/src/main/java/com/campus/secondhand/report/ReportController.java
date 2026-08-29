package com.campus.secondhand.report;

import com.campus.secondhand.common.ApiResponse;
import com.campus.secondhand.security.CurrentActorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reports")
public class ReportController {
    private final ContentGovernance governance;
    private final CurrentActorService actors;
    public ReportController(ContentGovernance governance, CurrentActorService actors) { this.governance = governance; this.actors = actors; }

    @PostMapping
    public ApiResponse<ContentGovernance.ReportView> submit(@Valid @RequestBody SubmitReportRequest request) {
        var actor = actors.require();
        return ApiResponse.ok(governance.submit(actor.userId(), new ContentGovernance.ReportDraft(
            request.targetType(), request.targetId(), request.reasonCode(), request.description(), request.contextConversationId())));
    }

    @GetMapping("/received")
    public ApiResponse<ContentGovernance.ReceivedReportPage> received(@RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(governance.listReceived(actors.require().userId(), page, size));
    }

    @GetMapping("/mine")
    public ApiResponse<ContentGovernance.ReportPage> mine(@RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(governance.listMine(actors.require().userId(), page, size));
    }

    public record SubmitReportRequest(@NotNull ReportTargetType targetType, @NotNull @Positive Long targetId,
                                      @NotNull ReportReason reasonCode,
                                      @NotBlank @Size(min = 10, max = 1000) String description,
                                      @Size(max = 36) String contextConversationId) {}
}
