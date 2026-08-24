package com.campus.secondhand.search;

import com.campus.secondhand.common.ApiResponse;
import com.campus.secondhand.user.UserRepository;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search")
public class SearchController {
    private final CampusSearch search;
    private final UserRepository users;

    public SearchController(CampusSearch search, UserRepository users) {
        this.search = search;
        this.users = users;
    }

    @GetMapping
    public ApiResponse<CampusSearch.SearchPage> search(
        @RequestParam(required = false, name = "q") String keywords,
        @RequestParam(defaultValue = "ITEMS") CampusSearch.Scope scope,
        @RequestParam(defaultValue = "RELEVANCE") CampusSearch.Sort sort,
        @RequestParam(required = false) BigDecimal minPrice,
        @RequestParam(required = false) BigDecimal maxPrice,
        @RequestParam(required = false) String region,
        @RequestParam(required = false) List<String> tags,
        @RequestParam(required = false) Long sellerId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "24") int size,
        Authentication authentication) {
        String viewerRegion = authentication == null ? null : users.findByEmailIgnoreCase(authentication.getName())
            .filter(user -> "ACTIVE".equals(user.getStatus())).map(user -> user.getCampusRegion()).orElse(null);
        CampusSearch.SearchQuery query = new CampusSearch.SearchQuery(keywords, scope, sort, minPrice, maxPrice,
            region, tags == null ? java.util.Set.of() : new LinkedHashSet<>(tags), sellerId, page, size);
        return ApiResponse.ok(search.search(query, viewerRegion));
    }
}
