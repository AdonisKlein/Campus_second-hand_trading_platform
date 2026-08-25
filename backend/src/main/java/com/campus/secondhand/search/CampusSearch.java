package com.campus.secondhand.search;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface CampusSearch {
    SearchPage search(SearchQuery query, String viewerRegion);

    enum Scope { ITEMS, USERS }

    enum Sort { RELEVANCE, NEWEST, ACTIVE, NEAREST, CREDIT, PRICE_ASC, PRICE_DESC }

    record SearchQuery(String keywords, Scope scope, Sort sort, BigDecimal minPrice, BigDecimal maxPrice,
                       String region, Set<String> tags, Long sellerId, int page, int size) {}

    record SearchPage(Scope scope, List<ItemHit> items, List<UserHit> users, int page, int size,
                      boolean hasNext, List<String> regions, List<String> tags) {}

    record ItemHit(Long id, String title, BigDecimal price, String description, String imageUrl,
                   String region, Set<String> tags, Long sellerId, String sellerNickname,
                   Integer sellerCreditScore, LocalDateTime sellerLastActiveAt, LocalDateTime createdAt) {}

    record UserHit(Long id, String username, String nickname, String region, Integer creditScore,
                   LocalDateTime lastActiveAt) {}
}
