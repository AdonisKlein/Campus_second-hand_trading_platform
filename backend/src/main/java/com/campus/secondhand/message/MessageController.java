package com.campus.secondhand.message;

import com.campus.secondhand.common.ApiResponse;
import com.campus.secondhand.user.User;
import com.campus.secondhand.user.UserRepository;
import com.campus.secondhand.security.CurrentActorService;
import com.campus.secondhand.item.ItemRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/messages")
public class MessageController {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final CurrentActorService actors;

    public MessageController(MessageRepository messageRepository, UserRepository userRepository,
                             ItemRepository itemRepository, CurrentActorService actors) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.itemRepository = itemRepository;
        this.actors = actors;
    }

    @GetMapping("/item/{itemId}")
    public ApiResponse<List<MessageView>> listByItem(@PathVariable Long itemId) {
        List<MessageView> messages = messageRepository.findByItemIdOrderByCreatedAtAsc(itemId)
            .stream()
            .map(this::toView)
            .toList();
        return ApiResponse.ok(messages);
    }

    @PostMapping
    public ApiResponse<Message> send(@Valid @RequestBody SendMessageRequest request) {
        Long senderId = actors.require().userId();
        var senderOptional = userRepository.findById(senderId);
        if (senderOptional.isEmpty()) {
            return ApiResponse.fail("发送用户不存在");
        }
        if ("DISABLED".equals(senderOptional.get().getStatus())) {
            return ApiResponse.fail("账号已被管理员禁用");
        }

        Message message = new Message();
        message.setItemId(request.itemId());
        message.setSenderId(senderId);
        var itemOptional = itemRepository.findById(request.itemId());
        if (itemOptional.isEmpty()) return ApiResponse.fail("物品不存在");
        var item = itemOptional.get();
        message.setReceiverId(item.getSellerId());
        message.setContent(request.content());
        return ApiResponse.created(messageRepository.save(message));
    }

    @PutMapping("/{id}")
    public ApiResponse<Message> update(@PathVariable Long id, @Valid @RequestBody UpdateMessageRequest request) {
        return messageRepository.findById(id)
            .map(message -> {
                if (!message.getSenderId().equals(actors.require().userId())) {
                    return ApiResponse.<Message>fail("只能修改自己的留言");
                }
                message.setContent(request.content());
                return ApiResponse.ok(messageRepository.save(message));
            })
            .orElseGet(() -> ApiResponse.fail("留言不存在"));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        return messageRepository.findById(id)
            .map(message -> {
                if (!message.getSenderId().equals(actors.require().userId())) {
                    return ApiResponse.<String>fail("只能删除自己的留言");
                }
                messageRepository.delete(message);
                return ApiResponse.ok("删除成功");
            })
            .orElseGet(() -> ApiResponse.fail("留言不存在"));
    }

    public record SendMessageRequest(
        @NotNull Long itemId,
        @NotBlank @Size(max = 500) String content
    ) {
    }

    public record UpdateMessageRequest(
        @NotBlank @Size(max = 500) String content
    ) {
    }

    public record MessageView(
        Long id,
        Long itemId,
        Long senderId,
        String senderNickname,
        Long receiverId,
        String content,
        LocalDateTime createdAt
    ) {
    }

    private MessageView toView(Message message) {
        String senderName = userRepository.findById(message.getSenderId())
            .map(this::displayName)
            .orElse("用户 " + message.getSenderId());

        return new MessageView(
            message.getId(),
            message.getItemId(),
            message.getSenderId(),
            senderName,
            message.getReceiverId(),
            message.getContent(),
            message.getCreatedAt()
        );
    }

    private String displayName(User user) {
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        return user.getUsername();
    }
}
