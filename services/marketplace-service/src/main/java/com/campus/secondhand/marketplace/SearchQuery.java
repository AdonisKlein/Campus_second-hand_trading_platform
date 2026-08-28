package com.campus.secondhand.marketplace;
import java.math.BigDecimal; import java.time.LocalDateTime; import java.util.*;
public record SearchQuery(String keywords, Scope scope, Sort sort, BigDecimal minPrice, BigDecimal maxPrice, String region, Set<String> tags, Long sellerId, int page, int size) { public enum Scope{ITEMS,USERS} public enum Sort{RELEVANCE,NEWEST,ACTIVE,NEAREST,CREDIT,PRICE_ASC,PRICE_DESC} }
