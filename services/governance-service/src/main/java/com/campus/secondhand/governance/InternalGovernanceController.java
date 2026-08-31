package com.campus.secondhand.governance;

import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/internal/governance")
class InternalGovernanceController {
    private final ContentGovernance governance;
    InternalGovernanceController(ContentGovernance governance){this.governance=governance;}
    @PostMapping("/action-results") ApiResponse<ContentGovernance.ReportView> result(@RequestBody ContentGovernance.ActionResult result){return ApiResponse.ok(governance.applyActionResult(result));}
}
