package com.campus.secondhand.search;

import com.campus.secondhand.item.MarketplaceOptions;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Pure, side-effect-free search input rules shared by the JPA adapter and unit tests. */
public final class SearchQueryRules {
    private SearchQueryRules() {}

    public static NormalizedQuery normalize(CampusSearch.SearchQuery raw) {
        CampusSearch.Scope scope = raw.scope() == null ? CampusSearch.Scope.ITEMS : raw.scope();
        CampusSearch.Sort sort = raw.sort() == null ? CampusSearch.Sort.RELEVANCE : raw.sort();
        if (scope == CampusSearch.Scope.USERS && (sort == CampusSearch.Sort.PRICE_ASC || sort == CampusSearch.Sort.PRICE_DESC)) {
            sort = CampusSearch.Sort.RELEVANCE;
        }
        if (raw.minPrice() != null && raw.minPrice().signum() < 0
            || raw.maxPrice() != null && raw.maxPrice().signum() < 0) {
            throw new SearchQueryException("价格不能小于 0");
        }
        if (raw.minPrice() != null && raw.maxPrice() != null && raw.minPrice().compareTo(raw.maxPrice()) > 0) {
            throw new SearchQueryException("最低价格不能高于最高价格");
        }
        String region = raw.region() == null || raw.region().isBlank() ? null : raw.region().trim();
        if (region != null && !MarketplaceOptions.REGIONS.contains(region)) throw new SearchQueryException("区域无效");
        Set<String> tags = raw.tags() == null ? Set.of() : new LinkedHashSet<>(raw.tags());
        if (!MarketplaceOptions.TAGS.containsAll(tags)) throw new SearchQueryException("标签无效");
        int page = Math.max(0, raw.page());
        int size = Math.min(48, Math.max(1, raw.size()));
        return new NormalizedQuery(terms(raw.keywords()), scope, sort, raw.minPrice(), raw.maxPrice(), region,
            Set.copyOf(tags), raw.sellerId(), page, size);
    }

    public static List<String> terms(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.trim().split("[\\s,，]+"))
            .map(value -> value.toLowerCase(Locale.ROOT)).filter(value -> !value.isBlank())
            .distinct().limit(8).toList();
    }

    public record NormalizedQuery(List<String> terms, CampusSearch.Scope scope, CampusSearch.Sort sort,
                                  BigDecimal minPrice, BigDecimal maxPrice, String region, Set<String> tags,
                                  Long sellerId, int page, int size) {}
}
