package com.campus.secondhand.marketplace;
import java.util.Optional;
public interface TradingInquiryPort {
    Optional<Inquiry> activeInquiry(long itemId, long buyerId);
    record Inquiry(long id, String status, String expiresAt) {}
}
