package com.campus.secondhand.item;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class MarketplaceOptions {
    public static final List<String> REGIONS = List.of("学院路校区", "沙河校区", "大运村", "其他校内区域");
    public static final List<String> TAGS = List.of("可小刀", "仅自提", "支持验货", "九成新", "急出", "免费赠送");

    private MarketplaceOptions() {}

    public static String normalizeRegion(String value) {
        String normalized = value == null || value.isBlank() ? REGIONS.getFirst() : value.trim();
        if (!REGIONS.contains(normalized)) throw new SellerInventoryRuleException("请选择平台支持的交易区域");
        return normalized;
    }

    public static Set<String> normalizeTags(Set<String> values) {
        if (values == null || values.isEmpty()) return Set.of();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String tag = value == null ? "" : value.trim();
            if (!TAGS.contains(tag)) throw new SellerInventoryRuleException("包含不支持的商品标签");
            normalized.add(tag);
        }
        if (normalized.size() > 4) throw new SellerInventoryRuleException("每件商品最多选择 4 个标签");
        return Set.copyOf(normalized);
    }
}
