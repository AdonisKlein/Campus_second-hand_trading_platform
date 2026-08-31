package com.campus.secondhand.governance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/reports")
class ReportController {
    private final ContentGovernance governance;private final CurrentActorService actors;
    ReportController(ContentGovernance governance,CurrentActorService actors){this.governance=governance;this.actors=actors;}
    @PostMapping ApiResponse<ContentGovernance.ReportView> submit(Authentication auth,@Valid @RequestBody SubmitReportRequest request){return ApiResponse.ok(governance.submit(actors.require(auth),new ContentGovernance.ReportDraft(request.targetType(),request.targetId(),request.reasonCode(),request.description())));}
    @GetMapping("/mine") ApiResponse<ContentGovernance.ReportPage> mine(Authentication auth,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return ApiResponse.ok(governance.listMine(actors.require(auth),page,size));}
    record SubmitReportRequest(@NotNull ReportTargetType targetType,@NotNull @Positive Long targetId,@NotNull ReportReason reasonCode,@NotBlank @Size(min=10,max=1000)String description){}
}
