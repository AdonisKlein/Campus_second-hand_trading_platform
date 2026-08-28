package com.campus.secondhand.marketplace;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class MarketplaceOptions {
    public static final List<String> REGIONS = List.of("学院路校区", "沙河校区", "大运村", "其他校内区域");
    public static final List<String> TAGS = List.of("可小刀", "仅自提", "支持验货", "九成新", "急出", "免费赠送");
    private MarketplaceOptions() { }
    public static String normalizeRegion(String region) {
        String value = region == null || region.isBlank() ? REGIONS.getFirst() : region.trim();
        if (!REGIONS.contains(value)) throw new MarketplaceException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_REGION", "区域无效");
        return value;
    }
    public static Set<String> normalizeTags(Set<String> tags) {
        Set<String> values = tags == null ? Set.of() : new LinkedHashSet<>(tags);
        if (values.size() > 4 || !TAGS.containsAll(values)) throw new MarketplaceException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_TAGS", "商品标签无效");
        return values;
    }
}
