package com.campus.secondhand.item;

import com.campus.secondhand.common.ApiResponse;
import com.campus.secondhand.user.User;
import com.campus.secondhand.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/items")
public class ItemController {

    private final ItemRepository itemRepository;
    private final UserRepository userRepository;

    public ItemController(ItemRepository itemRepository, UserRepository userRepository) {
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ApiResponse<List<Item>> list(@RequestParam(required = false) String category,
                                        @RequestParam(required = false) String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            return ApiResponse.ok(itemRepository.findByTitleContainingAndStatusOrderByCreatedAtDesc(keyword, "ON_SALE"));
        }
        if (category != null && !category.isBlank()) {
            return ApiResponse.ok(itemRepository.findByCategoryAndStatusOrderByCreatedAtDesc(category, "ON_SALE"));
        }
        return ApiResponse.ok(itemRepository.findByStatusOrderByCreatedAtDesc("ON_SALE"));
    }

    @GetMapping("/{id}")
    public ApiResponse<Item> detail(@PathVariable Long id) {
        return itemRepository.findById(id)
            .map(ApiResponse::ok)
            .orElseGet(() -> ApiResponse.fail("物品不存在"));
    }

    @PostMapping
    public ApiResponse<Item> publish(@Valid @RequestBody PublishItemRequest request) {
        var sellerOptional = userRepository.findById(request.sellerId());
        if (sellerOptional.isEmpty()) {
            return ApiResponse.fail("卖家不存在");
        }
        if (isDisabled(sellerOptional.get())) {
            return ApiResponse.fail("账号已被管理员禁用");
        }

        Item item = new Item();
        item.setTitle(request.title());
        item.setCategory(request.category());
        item.setPrice(request.price());
        item.setDescription(request.description());
        item.setImageUrl(request.imageUrl());
        item.setSellerId(request.sellerId());
        return ApiResponse.created(itemRepository.save(item));
    }

    public record PublishItemRequest(
        @NotBlank String title,
        @NotBlank String category,
        @NotNull BigDecimal price,
        String description,
        String imageUrl,
        @NotNull Long sellerId
    ) {
    }

    private boolean isDisabled(User user) {
        return "DISABLED".equals(user.getStatus());
    }
}
