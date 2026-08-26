package com.campus.secondhand.test;

import com.campus.secondhand.item.Item;
import com.campus.secondhand.item.ItemStatus;
import com.campus.secondhand.user.EmailVerification;
import com.campus.secondhand.user.User;
import com.campus.secondhand.user.VerificationPurpose;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Minimal, deterministic builders shared by unit and integration tests. */
public final class TestDataFactory {
    private TestDataFactory() { }

    public static User user(long id, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername("student" + id);
        user.setNickname("Student " + id);
        user.setEmail(email);
        user.setPasswordHash("encoded-password");
        return user;
    }

    public static Item item(long id, long sellerId, String title, BigDecimal price) {
        Item item = new Item();
        item.setId(id);
        item.setSellerId(sellerId);
        item.setTitle(title);
        item.setCategory("教材");
        item.setPrice(price);
        item.setStatus(ItemStatus.ON_SALE);
        return item;
    }

    public static EmailVerification verification(String email, VerificationPurpose purpose,
                                                  LocalDateTime expiresAt) {
        EmailVerification verification = new EmailVerification();
        verification.setEmail(email);
        verification.setPurpose(purpose);
        verification.setCodeHash("test-code-hash");
        verification.setCreatedAt(expiresAt.minusMinutes(5));
        verification.setExpiresAt(expiresAt);
        return verification;
    }
}
