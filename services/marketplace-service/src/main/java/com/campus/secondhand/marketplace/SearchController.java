package com.campus.secondhand.marketplace;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
public class SearchController {
    private final CampusSearch search;
    public SearchController(CampusSearch search) { this.search = search; }
    @GetMapping
    public ApiResponse<CampusSearch.SearchPage> search(@RequestParam(required=false,name="q") String keywords,
            @RequestParam(defaultValue="ITEMS") SearchQuery.Scope scope,
            @RequestParam(defaultValue="RELEVANCE") SearchQuery.Sort sort,
            @RequestParam(required=false) BigDecimal minPrice, @RequestParam(required=false) BigDecimal maxPrice,
            @RequestParam(required=false) String region, @RequestParam(required=false) List<String> tags,
            @RequestParam(required=false) Long sellerId, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="24") int size, Authentication authentication) {
        String viewerRegion = authentication != null && authentication.getPrincipal() instanceof Jwt jwt
                ? jwt.getClaimAsString("campus_region") : null;
        return ApiResponse.ok(search.search(new SearchQuery(keywords, scope, sort, minPrice, maxPrice, region,
                tags == null ? java.util.Set.of() : new LinkedHashSet<>(tags), sellerId, page, size), viewerRegion));
    }
}
