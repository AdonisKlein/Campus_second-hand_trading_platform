package com.campus.secondhand.unit.search;

import com.campus.secondhand.search.CampusSearch;
import com.campus.secondhand.search.SearchQueryException;
import com.campus.secondhand.search.SearchQueryRules;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SearchQueryRulesTest {
    @Test
    void splitsSeveralKeywordsCaseInsensitivelyAndKeepsOnlyEightDistinctTerms() {
        String raw = "耳机, 蓝牙  耳机 书包 数码 台灯 椅子 键盘 鼠标 相机 书籍";

        assertEquals(8, SearchQueryRules.terms(raw).size());
        assertEquals(java.util.List.of("耳机", "蓝牙", "书包", "数码", "台灯", "椅子", "键盘", "鼠标"),
            SearchQueryRules.terms(raw));
    }

    @Test
    void normalizesUserPriceSortToRelevanceAndClampsPaging() {
        var normalized = SearchQueryRules.normalize(new CampusSearch.SearchQuery(
            "alice", CampusSearch.Scope.USERS, CampusSearch.Sort.PRICE_DESC,
            null, null, null, Set.of(), null, -4, 1000));

        assertEquals(CampusSearch.Sort.RELEVANCE, normalized.sort());
        assertEquals(0, normalized.page());
        assertEquals(48, normalized.size());
    }

    @Test
    void rejectsInvalidPriceRangeAndNegativePrice() {
        var reversed = new CampusSearch.SearchQuery("", CampusSearch.Scope.ITEMS, CampusSearch.Sort.PRICE_ASC,
            new BigDecimal("20"), new BigDecimal("10"), null, Set.of(), null, 0, 20);
        var negative = new CampusSearch.SearchQuery("", CampusSearch.Scope.ITEMS, CampusSearch.Sort.PRICE_ASC,
            new BigDecimal("-1"), null, null, Set.of(), null, 0, 20);

        assertAll(
            () -> assertThrows(SearchQueryException.class, () -> SearchQueryRules.normalize(reversed)),
            () -> assertThrows(SearchQueryException.class, () -> SearchQueryRules.normalize(negative))
        );
    }

    @Test
    void itemSearchPreservesBothPriceSortDirections() {
        var ascending = SearchQueryRules.normalize(new CampusSearch.SearchQuery(
            "教材", CampusSearch.Scope.ITEMS, CampusSearch.Sort.PRICE_ASC,
            BigDecimal.ZERO, new BigDecimal("100"), null, Set.of(), null, 0, 20));
        var descending = SearchQueryRules.normalize(new CampusSearch.SearchQuery(
            "教材", CampusSearch.Scope.ITEMS, CampusSearch.Sort.PRICE_DESC,
            BigDecimal.ZERO, new BigDecimal("100"), null, Set.of(), null, 0, 20));

        assertAll(
            () -> assertEquals(CampusSearch.Sort.PRICE_ASC, ascending.sort()),
            () -> assertEquals(CampusSearch.Sort.PRICE_DESC, descending.sort()),
            () -> assertEquals(BigDecimal.ZERO, ascending.minPrice()),
            () -> assertEquals(new BigDecimal("100"), ascending.maxPrice())
        );
    }
}
