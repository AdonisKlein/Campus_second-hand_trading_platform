package com.campus.secondhand.marketplace;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MarketplaceRulesTest {
    @Test void keywordInputBecomesSeveralStableTerms() {
        assertThat(SearchQueryRules.terms("  台灯, 宿舍，台灯  ")).containsExactly("台灯", "宿舍");
    }

    @Test void invalidPriceRangeAndUnknownTagsAreRejected() {
        assertThatThrownBy(() -> SearchQueryRules.normalize(new SearchQuery(null, SearchQuery.Scope.ITEMS,
                SearchQuery.Sort.PRICE_ASC, new BigDecimal("20"), new BigDecimal("10"), null,
                Set.of(), null, 0, 24))).hasMessageContaining("最低价格");
        assertThatThrownBy(() -> MarketplaceOptions.normalizeTags(Set.of("平台不存在的标签")))
                .isInstanceOf(MarketplaceException.class);
    }
}
