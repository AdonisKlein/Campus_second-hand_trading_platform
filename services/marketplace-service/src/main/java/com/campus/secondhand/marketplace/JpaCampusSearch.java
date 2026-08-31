package com.campus.secondhand.marketplace;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class JpaCampusSearch implements CampusSearch {
    @PersistenceContext private EntityManager entityManager;
    private final SearchableUserProjectionRepository projections;
    public JpaCampusSearch(SearchableUserProjectionRepository projections) { this.projections = projections; }

    @Override public SearchPage search(SearchQuery raw, String viewerRegion) {
        SearchQuery query = SearchQueryRules.normalize(raw);
        return query.scope() == SearchQuery.Scope.USERS ? users(query, viewerRegion) : items(query, viewerRegion);
    }

    private SearchPage items(SearchQuery query, String viewerRegion) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Item> criteria = cb.createQuery(Item.class);
        Root<Item> item = criteria.from(Item.class);
        Join<Item, SearchableUserProjection> seller = item.join("sellerProjection");
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(item.get("status"), ItemStatus.ON_SALE));
        predicates.add(cb.equal(item.get("moderationStatus"), ItemModerationStatus.VISIBLE));
        predicates.add(cb.equal(seller.get("status"), "ACTIVE"));
        if (query.sellerId() != null) predicates.add(cb.equal(item.get("sellerId"), query.sellerId()));
        if (query.minPrice() != null) predicates.add(cb.greaterThanOrEqualTo(item.get("price"), query.minPrice()));
        if (query.maxPrice() != null) predicates.add(cb.lessThanOrEqualTo(item.get("price"), query.maxPrice()));
        if (query.region() != null) predicates.add(cb.equal(item.get("region"), query.region()));
        for (String tag : query.tags()) predicates.add(cb.isMember(tag, item.get("tags")));
        for (String term : SearchQueryRules.terms(query.keywords())) {
            String pattern = pattern(term);
            predicates.add(cb.or(cb.like(cb.lower(item.get("title")), pattern, '\\'),
                    cb.like(cb.lower(cb.coalesce(item.get("description"), "")), pattern, '\\'),
                    cb.isMember(term, item.get("tags"))));
        }
        criteria.where(predicates.toArray(Predicate[]::new));
        criteria.orderBy(itemOrders(query, viewerRegion, cb, item, seller));
        List<Item> found = entityManager.createQuery(criteria).setFirstResult(query.page() * query.size())
                .setMaxResults(query.size() + 1).getResultList();
        boolean next = found.size() > query.size();
        if (next) found = new ArrayList<>(found.subList(0, query.size()));
        Map<Long, SearchableUserProjection> sellers = projections.findAllById(found.stream().map(Item::getSellerId).toList())
                .stream().collect(Collectors.toMap(SearchableUserProjection::getId, Function.identity()));
        List<ItemHit> hits = found.stream().map(value -> hit(value, sellers.get(value.getSellerId()))).toList();
        return new SearchPage(SearchQuery.Scope.ITEMS, hits, List.of(), query.page(), query.size(), next,
                MarketplaceOptions.REGIONS, MarketplaceOptions.TAGS);
    }

    private SearchPage users(SearchQuery query, String viewerRegion) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<SearchableUserProjection> criteria = cb.createQuery(SearchableUserProjection.class);
        Root<SearchableUserProjection> user = criteria.from(SearchableUserProjection.class);
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(user.get("status"), "ACTIVE"));
        predicates.add(cb.equal(user.get("role"), "STUDENT"));
        if (query.region() != null) predicates.add(cb.equal(user.get("campusRegion"), query.region()));
        for (String term : SearchQueryRules.terms(query.keywords())) {
            String p = pattern(term);
            predicates.add(cb.or(cb.like(cb.lower(user.get("username")), p, '\\'),
                    cb.like(cb.lower(cb.coalesce(user.get("nickname"), "")), p, '\\')));
        }
        criteria.where(predicates.toArray(Predicate[]::new));
        List<Order> orders = new ArrayList<>();
        if (query.sort() == SearchQuery.Sort.ACTIVE) orders.add(cb.desc(user.get("lastActiveAt")));
        if (query.sort() == SearchQuery.Sort.CREDIT) orders.add(cb.desc(user.get("creditScore")));
        if (query.sort() == SearchQuery.Sort.NEAREST && viewerRegion != null)
            orders.add(cb.asc(cb.<Integer>selectCase().when(cb.equal(user.get("campusRegion"), viewerRegion), 0).otherwise(1)));
        if (query.sort() == SearchQuery.Sort.RELEVANCE && !SearchQueryRules.terms(query.keywords()).isEmpty())
            orders.add(cb.desc(userRelevance(query, cb, user)));
        orders.add(cb.desc(user.get("createdAt"))); orders.add(cb.desc(user.get("id"))); criteria.orderBy(orders);
        List<SearchableUserProjection> found = entityManager.createQuery(criteria)
                .setFirstResult(query.page() * query.size()).setMaxResults(query.size() + 1).getResultList();
        boolean next = found.size() > query.size(); if (next) found = found.subList(0, query.size());
        return new SearchPage(SearchQuery.Scope.USERS, List.of(), found.stream().map(value -> new UserHit(value.getId(),
                value.getUsername(), value.getNickname(), value.getCampusRegion(), value.getCreditScore(),
                value.getLastActiveAt())).toList(), query.page(), query.size(), next,
                MarketplaceOptions.REGIONS, MarketplaceOptions.TAGS);
    }

    private List<Order> itemOrders(SearchQuery q, String region, CriteriaBuilder cb, Root<Item> item,
                                   From<?, SearchableUserProjection> seller) {
        List<Order> orders = new ArrayList<>();
        switch (q.sort()) {
            case ACTIVE -> orders.add(cb.desc(seller.get("lastActiveAt")));
            case CREDIT -> orders.add(cb.desc(seller.get("creditScore")));
            case PRICE_ASC -> orders.add(cb.asc(item.get("price")));
            case PRICE_DESC -> orders.add(cb.desc(item.get("price")));
            case NEAREST -> { if (region != null) orders.add(cb.asc(cb.<Integer>selectCase().when(cb.equal(item.get("region"), region), 0).otherwise(1))); }
            case RELEVANCE -> {
                if (!SearchQueryRules.terms(q.keywords()).isEmpty()) {
                    orders.add(cb.desc(itemRelevance(q, cb, item)));
                }
            }
            default -> { }
        }
        orders.add(cb.desc(item.get("createdAt"))); orders.add(cb.desc(item.get("id"))); return orders;
    }
    private ItemHit hit(Item item, SearchableUserProjection seller) {
        return new ItemHit(item.getId(), item.getTitle(), item.getPrice(), item.getDescription(), item.getImageUrl(),
                item.getRegion(), Set.copyOf(item.getTags()), item.getSellerId(), seller == null ? null : seller.getNickname(),
                seller == null ? 0 : seller.getCreditScore(), seller == null ? null : seller.getLastActiveAt(), item.getCreatedAt());
    }
    private Expression<Integer> itemRelevance(SearchQuery query,CriteriaBuilder cb,Root<Item> item){Expression<Integer> score=cb.literal(0);for(String term:SearchQueryRules.terms(query.keywords())){String p=pattern(term);Expression<Integer> value=cb.<Integer>selectCase().when(cb.equal(cb.lower(item.get("title")),term),10).when(cb.like(cb.lower(item.get("title")),p,'\\'),5).when(cb.like(cb.lower(cb.coalesce(item.get("description"),"")),p,'\\'),2).otherwise(0);score=cb.sum(score,value);}return score;}
    private Expression<Integer> userRelevance(SearchQuery query,CriteriaBuilder cb,Root<SearchableUserProjection> user){Expression<Integer> score=cb.literal(0);for(String term:SearchQueryRules.terms(query.keywords())){String p=pattern(term);Expression<Integer> value=cb.<Integer>selectCase().when(cb.equal(cb.lower(user.get("username")),term),10).when(cb.like(cb.lower(cb.coalesce(user.get("nickname"),"")),p,'\\'),5).when(cb.like(cb.lower(user.get("username")),p,'\\'),3).otherwise(0);score=cb.sum(score,value);}return score;}
    private String pattern(String value) { return "%" + value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"; }
}
