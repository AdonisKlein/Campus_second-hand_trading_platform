package com.campus.secondhand.chat;

import com.campus.secondhand.common.ApiResponse;
import com.campus.secondhand.security.CurrentActorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/chat")
public class ChatController {
    private final DirectChat chat;
    private final CurrentActorService actors;
    public ChatController(DirectChat chat, CurrentActorService actors) { this.chat = chat; this.actors = actors; }

    @PostMapping("/conversations")
    public ApiResponse<DirectChat.ConversationView> open(@Valid @RequestBody OpenRequest request) {
        return ApiResponse.ok(chat.open(actors.require().userId(), request.itemId()));
    }
    @PostMapping("/order-conversations")
    public ApiResponse<DirectChat.ConversationView> openTrade(@Valid @RequestBody OpenTradeRequest request) {
        return ApiResponse.ok(chat.openTrade(actors.require().userId(), request.orderId()));
    }
    @GetMapping("/conversations")
    public ApiResponse<DirectChat.ConversationPage> conversations(@RequestParam(defaultValue="0") int page,
                                                                   @RequestParam(defaultValue="30") int size) {
        return ApiResponse.ok(chat.conversations(actors.require().userId(), page, size));
    }
    @GetMapping("/conversations/{id}/messages")
    public ApiResponse<DirectChat.MessagePage> history(@PathVariable String id, @RequestParam(required=false) Long beforeSequence,
                                                       @RequestParam(defaultValue="50") int size) {
        return ApiResponse.ok(chat.history(actors.require().userId(), id, beforeSequence, size));
    }
    @PostMapping("/conversations/{id}/messages")
    public ApiResponse<DirectChat.MessageView> send(@PathVariable String id, @Valid @RequestBody SendRequest request) {
        return ApiResponse.ok(chat.send(actors.require().userId(), id, request.body()));
    }
    @PostMapping("/conversations/{id}/read")
    public ApiResponse<DirectChat.ConversationView> read(@PathVariable String id, @Valid @RequestBody ReadRequest request) {
        return ApiResponse.ok(chat.markRead(actors.require().userId(), id, request.throughSequence()));
    }
    @PutMapping("/blocks/{userId}") public ApiResponse<Void> block(@PathVariable Long userId) {
        chat.block(actors.require().userId(), userId); return ApiResponse.ok(null);
    }
    @DeleteMapping("/blocks/{userId}") public ApiResponse<Void> unblock(@PathVariable Long userId) {
        chat.unblock(actors.require().userId(), userId); return ApiResponse.ok(null);
    }
    @GetMapping("/unread-count") public ApiResponse<Map<String,Long>> unread() {
        return ApiResponse.ok(Map.of("count", chat.unreadTotal(actors.require().userId())));
    }
    public record OpenRequest(@NotNull Long itemId) {}
    public record OpenTradeRequest(@NotNull Long orderId) {}
    public record SendRequest(@NotBlank @Size(max=2000) String body) {}
    public record ReadRequest(@NotNull Long throughSequence) {}
}
