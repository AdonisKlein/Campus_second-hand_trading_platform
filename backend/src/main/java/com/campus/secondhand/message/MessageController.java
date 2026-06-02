package com.campus.secondhand.message;

import com.campus.secondhand.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/messages")
public class MessageController {

    private final MessageRepository messageRepository;

    public MessageController(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @GetMapping("/item/{itemId}")
    public ApiResponse<List<Message>> listByItem(@PathVariable Long itemId) {
        return ApiResponse.ok(messageRepository.findByItemIdOrderByCreatedAtAsc(itemId));
    }

    @PostMapping
    public ApiResponse<Message> send(@Valid @RequestBody SendMessageRequest request) {
        Message message = new Message();
        message.setItemId(request.itemId());
        message.setSenderId(request.senderId());
        message.setReceiverId(request.receiverId());
        message.setContent(request.content());
        return ApiResponse.created(messageRepository.save(message));
    }

    public record SendMessageRequest(
        @NotNull Long itemId,
        @NotNull Long senderId,
        @NotNull Long receiverId,
        @NotBlank String content
    ) {
    }
}

