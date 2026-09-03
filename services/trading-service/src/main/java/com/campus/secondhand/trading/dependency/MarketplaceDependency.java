package com.campus.secondhand.trading.dependency;

import java.util.Map;

/**
 * Trading 访问 Marketplace 的唯一读取 seam。超时、GET 重试、熔断、错误映射和
 * correlationId 传播都留在 implementation 内，调用方只描述业务操作。
 */
public interface MarketplaceDependency {
    Map<String, Object> executeRead(String operation, String path, Object... uriVariables);
}
