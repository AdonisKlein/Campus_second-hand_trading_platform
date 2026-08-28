package com.campus.secondhand.marketplace;

import com.campus.secondhand.marketplace.message.MessageRepository;
import java.nio.charset.StandardCharsets;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/internal/messages")
class InternalMessageController {
    private final MessageRepository messages;
    private final MarketplaceProperties props;
    InternalMessageController(MessageRepository messages, MarketplaceProperties props) { this.messages = messages; this.props = props; }

    @GetMapping("/{id}/governance-snapshot")
    ApiResponse<Snapshot> snapshot(@PathVariable Long id, @RequestHeader(value="X-Internal-Service-Token", required=false) String token) {
        check(token);
        return messages.findById(id).map(m -> ApiResponse.ok(new Snapshot(m.getId(), m.getSenderId(), m.getContent(), "MESSAGE", true)))
            .orElseGet(() -> ApiResponse.fail("留言不存在或不可举报"));
    }
    private void check(String token) {
        if (token == null || !java.security.MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), props.internalServiceToken().getBytes(StandardCharsets.UTF_8)))
            throw new AccessDeniedException("internal only");
    }
    record Snapshot(Long targetId, Long reportedUserId, String summary, String targetType, boolean reportable) {}
}
