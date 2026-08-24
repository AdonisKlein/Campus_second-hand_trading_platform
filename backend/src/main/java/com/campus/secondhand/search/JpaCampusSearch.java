package com.campus.secondhand.search;

import com.campus.secondhand.item.Item;
import com.campus.secondhand.item.ItemModerationStatus;
import com.campus.secondhand.item.ItemStatus;
import com.campus.secondhand.item.MarketplaceOptions;
import com.campus.secondhand.user.User;
import com.campus.secondhand.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class JpaCampusSearch implements CampusSearch {
    @PersistenceContext
    private EntityManager entityManager;
    private final UserRepository users;

    public JpaCampusSearch(UserRepository users) {
        this.users = users;
    }

    @Override
    public SearchPage search(SearchQuery raw, String viewerRegion) {
        NormalizedQuery query = normalize(raw);
        return query.scope() == Scope.USERS ? searchUsers(query, viewerRegion) : searchItems(query, viewerRegion);
    }

    private SearchPage searchItems(NormalizedQuery query, String viewerRegion) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Item> criteria = cb.createQuery(Item.class);
        Root<Item> item = criteria.from(Item.class);
        Root<User> seller = criteria.from(User.class);
        List<Predicate> predicates = publicItemPredicates(query, cb, item, seller);
        criteria.select(item).where(predicates.toArray(Predicate[]::new));
        criteria.orderBy(itemOrders(query, viewerRegion, cb, item, seller));

        List<Item> found = entityManager.createQuery(criteria)
            .setFirstResult(query.page() * query.size()).setMaxResults(query.size() + 1).getResultList();
        boolean hasNext = found.size() > query.size();
        if (hasNext) found = new ArrayList<>(found.subList(0, query.size()));

        Map<Long, User> sellerById = users.findAllById(found.stream().map(Item::getSellerId).distinct().toList())
            .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        List<ItemHit> hits = found.stream().map(value -> itemHit(value, sellerById.get(value.getSellerId()))).toList();
        return new SearchPage(Scope.ITEMS, hits, List.of(), query.page(), query.size(), hasNext,
            MarketplaceOptions.REGIONS, MarketplaceOptions.TAGS);
    }

    private SearchPage searchUsers(NormalizedQuery query, String viewerRegion) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> criteria = cb.createQuery(User.class);
        Root<User> user = criteria.from(User.class);
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(user.get("status"), "ACTIVE"));
        predicates.add(cb.equal(user.get("role"), "STUDENT"));
        if (query.region() != null) predicates.add(cb.equal(user.get("campusRegion"), query.region()));
        for (String term : query.terms()) {
            String pattern = containsPattern(term);
            predicates.add(cb.or(
                cb.like(cb.lower(user.get("username")), pattern, '\\'),
                cb.like(cb.lower(cb.coalesce(user.get("nickname"), "")), pattern, '\\')
            ));
        }
        criteria.where(predicates.toArray(Predicate[]::new));
        criteria.orderBy(userOrders(query, viewerRegion, cb, user));
        List<User> found = entityManager.createQuery(criteria)
            .setFirstResult(query.page() * query.size()).setMaxResults(query.size() + 1).getResultList();
        boolean hasNext = found.size() > query.size();
        if (hasNext) found = new ArrayList<>(found.subList(0, query.size()));
        List<UserHit> hits = found.stream().map(userValue -> new UserHit(userValue.getId(), userValue.getUsername(),
            userValue.getNickname(), userValue.getCampusRegion(), userValue.getCreditScore(),
            userValue.getLastActiveAt())).toList();
        return new SearchPage(Scope.USERS, List.of(), hits, query.page(), query.size(), hasNext,
            MarketplaceOptions.REGIONS, MarketplaceOptions.TAGS);
    }

    private List<Predicate> publicItemPredicates(NormalizedQuery query, CriteriaBuilder cb,
                                                  Root<Item> item, Root<User> seller) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(item.get("sellerId"), seller.get("id")));
        predicates.add(cb.equal(item.get("status"), ItemStatus.ON_SALE));
        predicates.add(cb.equal(item.get("moderationStatus"), ItemModerationStatus.VISIBLE));
        predicates.add(cb.equal(seller.get("status"), "ACTIVE"));
        if (query.sellerId() != null) predicates.add(cb.equal(item.get("sellerId"), query.sellerId()));
        if (query.minPrice() != null) predicates.add(cb.greaterThanOrEqualTo(item.get("price"), query.minPrice()));
        if (query.maxPrice() != null) predicates.add(cb.lessThanOrEqualTo(item.get("price"), query.maxPrice()));
        if (query.region() != null) predicates.add(cb.equal(item.get("region"), query.region()));
        for (String tag : query.tags()) predicates.add(cb.isMember(tag, item.get("tags")));
        for (String term : query.terms()) {
            String pattern = containsPattern(term);
            predicates.add(cb.or(
                cb.like(cb.lower(item.get("title")), pattern, '\\'),
                cb.like(cb.lower(cb.coalesce(item.get("description"), "")), pattern, '\\'),
                cb.isMember(term, item.get("tags"))
            ));
        }
        return predicates;
    }

    private List<Order> itemOrders(NormalizedQuery query, String viewerRegion, CriteriaBuilder cb,
                                   Root<Item> item, Root<User> seller) {
        List<Order> orders = new ArrayList<>();
        switch (query.sort()) {
            case ACTIVE -> orders.add(cb.desc(seller.get("lastActiveAt")));
            case NEAREST -> addRegionFirst(orders, viewerRegion, cb, item.get("region"));
            case CREDIT -> orders.add(cb.desc(seller.get("creditScore")));
            case PRICE_ASC -> orders.add(cb.asc(item.get("price")));
            case PRICE_DESC -> orders.add(cb.desc(item.get("price")));
            case RELEVANCE -> orders.add(cb.desc(itemRelevance(query, cb, item)));
            case NEWEST -> { }
        }
        orders.add(cb.desc(item.get("createdAt")));
        orders.add(cb.desc(item.get("id")));
        return orders;
    }

    private List<Order> userOrders(NormalizedQuery query, String viewerRegion, CriteriaBuilder cb, Root<User> user) {
        List<Order> orders = new ArrayList<>();
        switch (query.sort()) {
            case ACTIVE -> orders.add(cb.desc(user.get("lastActiveAt")));
            case NEAREST -> addRegionFirst(orders, viewerRegion, cb, user.get("campusRegion"));
            case CREDIT -> orders.add(cb.desc(user.get("creditScore")));
            case RELEVANCE -> orders.add(cb.desc(userRelevance(query, cb, user)));
            default -> { }
        }
        orders.add(cb.desc(user.get("createdAt")));
        orders.add(cb.desc(user.get("id")));
        return orders;
    }

    private Expression<Integer> itemRelevance(NormalizedQuery query, CriteriaBuilder cb, Root<Item> item) {
        Expression<Integer> score = cb.literal(0);
        for (String term : query.terms()) {
            String pattern = containsPattern(term);
            Expression<Integer> termScore = cb.<Integer>selectCase()
                .when(cb.equal(cb.lower(item.get("title")), term), 10)
                .when(cb.like(cb.lower(item.get("title")), pattern, '\\'), 5)
                .when(cb.like(cb.lower(cb.coalesce(item.get("description"), "")), pattern, '\\'), 2)
                .otherwise(0);
            score = cb.sum(score, termScore);
        }
        return score;
    }

    private Expression<Integer> userRelevance(NormalizedQuery query, CriteriaBuilder cb, Root<User> user) {
        Expression<Integer> score = cb.literal(0);
        for (String term : query.terms()) {
            String pattern = containsPattern(term);
            Expression<Integer> termScore = cb.<Integer>selectCase()
                .when(cb.equal(cb.lower(user.get("username")), term), 10)
                .when(cb.like(cb.lower(cb.coalesce(user.get("nickname"), "")), pattern, '\\'), 5)
                .when(cb.like(cb.lower(user.get("username")), pattern, '\\'), 3)
                .otherwise(0);
            score = cb.sum(score, termScore);
        }
        return score;
    }

    private void addRegionFirst(List<Order> orders, String viewerRegion, CriteriaBuilder cb,
                                Expression<String> region) {
        if (viewerRegion != null && !viewerRegion.isBlank()) {
            orders.add(cb.asc(cb.<Integer>selectCase().when(cb.equal(region, viewerRegion), 0).otherwise(1)));
        }
    }

    private ItemHit itemHit(Item item, User seller) {
        return new ItemHit(item.getId(), item.getTitle(), item.getPrice(), item.getDescription(), item.getImageUrl(),
            item.getRegion(), Set.copyOf(item.getTags()), item.getSellerId(),
            seller == null ? null : seller.getNickname(), seller == null ? 0 : seller.getCreditScore(),
            seller == null ? null : seller.getLastActiveAt(), item.getCreatedAt());
    }

    private NormalizedQuery normalize(SearchQuery raw) {
        Scope scope = raw.scope() == null ? Scope.ITEMS : raw.scope();
        Sort sort = raw.sort() == null ? Sort.RELEVANCE : raw.sort();
        if (scope == Scope.USERS && (sort == Sort.PRICE_ASC || sort == Sort.PRICE_DESC)) sort = Sort.RELEVANCE;
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

    private List<String> terms(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return java.util.Arrays.stream(raw.trim().split("[\\s,，]+"))
            .map(value -> value.toLowerCase(Locale.ROOT)).filter(value -> !value.isBlank())
            .distinct().limit(8).toList();
    }

    private String containsPattern(String term) {
        return "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private record NormalizedQuery(List<String> terms, Scope scope, Sort sort, BigDecimal minPrice,
                                   BigDecimal maxPrice, String region, Set<String> tags, Long sellerId,
                                   int page, int size) {}
}
