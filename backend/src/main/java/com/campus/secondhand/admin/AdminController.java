package com.campus.secondhand.admin;

import com.campus.secondhand.common.ApiResponse;
import com.campus.secondhand.item.Item;
import com.campus.secondhand.item.ItemRepository;
import com.campus.secondhand.message.Message;
import com.campus.secondhand.message.MessageRepository;
import com.campus.secondhand.user.User;
import com.campus.secondhand.user.UserController.UserView;
import com.campus.secondhand.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final ItemRepository itemRepository;

    public AdminController(UserRepository userRepository,
                           MessageRepository messageRepository,
                           ItemRepository itemRepository) {
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.itemRepository = itemRepository;
    }

    @GetMapping("/users")
    public ApiResponse<List<UserView>> listUsers(@RequestParam Long adminId) {
        ApiResponse<User> auth = requireAdmin(adminId);
        if (!auth.success()) {
            return ApiResponse.fail(auth.message());
        }

        List<UserView> users = userRepository.findAll()
            .stream()
            .filter(user -> !"ADMIN".equals(user.getRole()))
            .map(UserView::from)
            .toList();
        return ApiResponse.ok(users);
    }

    @PutMapping("/users/{id}/status")
    public ApiResponse<UserView> updateUserStatus(@PathVariable Long id,
                                                  @Valid @RequestBody UpdateUserStatusRequest request) {
        ApiResponse<User> auth = requireAdmin(request.adminId());
        if (!auth.success()) {
            return ApiResponse.fail(auth.message());
        }

        if (!"ACTIVE".equals(request.status()) && !"DISABLED".equals(request.status())) {
            return ApiResponse.fail("用户状态不合法");
        }

        return userRepository.findById(id)
            .map(user -> {
                if ("ADMIN".equals(user.getRole())) {
                    return ApiResponse.<UserView>fail("不能修改管理员账号");
                }
                user.setStatus(request.status());
                return ApiResponse.ok(UserView.from(userRepository.save(user)));
            })
            .orElseGet(() -> ApiResponse.fail("用户不存在"));
    }

    @GetMapping("/messages")
    public ApiResponse<List<AdminMessageView>> listMessages(@RequestParam Long adminId) {
        ApiResponse<User> auth = requireAdmin(adminId);
        if (!auth.success()) {
            return ApiResponse.fail(auth.message());
        }

        List<AdminMessageView> messages = messageRepository.findAllByOrderByCreatedAtDesc()
            .stream()
            .map(this::toMessageView)
            .toList();
        return ApiResponse.ok(messages);
    }

    @DeleteMapping("/messages/{id}")
    public ApiResponse<String> deleteMessage(@PathVariable Long id,
                                             @Valid @RequestBody AdminActionRequest request) {
        ApiResponse<User> auth = requireAdmin(request.adminId());
        if (!auth.success()) {
            return ApiResponse.fail(auth.message());
        }

        return messageRepository.findById(id)
            .map(message -> {
                messageRepository.delete(message);
                return ApiResponse.ok("删除成功");
            })
            .orElseGet(() -> ApiResponse.fail("留言不存在"));
    }

    @GetMapping("/items")
    public ApiResponse<List<Item>> listItems(@RequestParam Long adminId) {
        ApiResponse<User> auth = requireAdmin(adminId);
        if (!auth.success()) {
            return ApiResponse.fail(auth.message());
        }
        return ApiResponse.ok(itemRepository.findAllByOrderByCreatedAtDesc());
    }

    @PutMapping("/items/{id}/status")
    public ApiResponse<Item> updateItemStatus(@PathVariable Long id,
                                              @Valid @RequestBody UpdateItemStatusRequest request) {
        ApiResponse<User> auth = requireAdmin(request.adminId());
        if (!auth.success()) {
            return ApiResponse.fail(auth.message());
        }

        if (!"ON_SALE".equals(request.status()) && !"REMOVED".equals(request.status())) {
            return ApiResponse.fail("商品状态不合法");
        }

        return itemRepository.findById(id)
            .map(item -> {
                item.setStatus(request.status());
                return ApiResponse.ok(itemRepository.save(item));
            })
            .orElseGet(() -> ApiResponse.fail("物品不存在"));
    }

    private ApiResponse<User> requireAdmin(Long adminId) {
        return userRepository.findById(adminId)
            .map(user -> {
                if (!"ADMIN".equals(user.getRole())) {
                    return ApiResponse.<User>fail("无管理员权限");
                }
                if ("DISABLED".equals(user.getStatus())) {
                    return ApiResponse.<User>fail("管理员账号已被禁用");
                }
                return ApiResponse.ok(user);
            })
            .orElseGet(() -> ApiResponse.fail("管理员不存在"));
    }

    private AdminMessageView toMessageView(Message message) {
        String senderName = userRepository.findById(message.getSenderId())
            .map(this::displayName)
            .orElse("用户 " + message.getSenderId());

        return new AdminMessageView(
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

    public record UpdateUserStatusRequest(@NotNull Long adminId, @NotNull String status) {
    }

    public record UpdateItemStatusRequest(@NotNull Long adminId, @NotNull String status) {
    }

    public record AdminActionRequest(@NotNull Long adminId) {
    }

    public record AdminMessageView(
        Long id,
        Long itemId,
        Long senderId,
        String senderNickname,
        Long receiverId,
        String content,
        LocalDateTime createdAt
    ) {
    }
}
