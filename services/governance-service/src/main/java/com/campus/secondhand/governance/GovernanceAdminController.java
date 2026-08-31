package com.campus.secondhand.governance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/reports")
class GovernanceAdminController {
    private final ContentGovernance governance;private final CurrentActorService actors;
    GovernanceAdminController(ContentGovernance governance,CurrentActorService actors){this.governance=governance;this.actors=actors;}
    @GetMapping ApiResponse<ContentGovernance.ReportPage> list(Authentication auth,@RequestParam(required=false)ReportStatus status,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="30")int size){return ApiResponse.ok(governance.listForAdmin(actors.require(auth),status,page,size));}
    @PutMapping("/{id}") ApiResponse<ContentGovernance.ReportView> decide(Authentication auth,@PathVariable long id,@Valid @RequestBody DecisionRequest request){return ApiResponse.ok(governance.decide(actors.require(auth),id,new ContentGovernance.Decision(request.status(),request.action(),request.note())));}
    record DecisionRequest(@NotNull ReportStatus status,GovernanceAction action,@NotBlank @Size(min=2,max=1000)String note){}
}
