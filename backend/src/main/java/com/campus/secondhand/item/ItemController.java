package com.campus.secondhand.item;

import com.campus.secondhand.common.ApiResponse;
import com.campus.secondhand.security.CurrentActorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/items")
public class ItemController {

    private final ItemRepository itemRepository;
    private final SellerInventory sellerInventory;
    private final CurrentActorService actors;

    public ItemController(ItemRepository itemRepository, SellerInventory sellerInventory, CurrentActorService actors) {
        this.itemRepository = itemRepository;
        this.sellerInventory = sellerInventory;
        this.actors = actors;
    }

    @GetMapping
    public ApiResponse<List<Item>> list(@RequestParam(required = false) String category,
                                        @RequestParam(required = false) String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            return ApiResponse.ok(itemRepository.findByTitleContainingAndStatusAndModerationStatusOrderByCreatedAtDesc(
                keyword, ItemStatus.ON_SALE, ItemModerationStatus.VISIBLE));
        }
        if (category != null && !category.isBlank()) {
            return ApiResponse.ok(itemRepository.findByCategoryAndStatusAndModerationStatusOrderByCreatedAtDesc(
                category, ItemStatus.ON_SALE, ItemModerationStatus.VISIBLE));
        }
        return ApiResponse.ok(itemRepository.findByStatusAndModerationStatusOrderByCreatedAtDesc(
            ItemStatus.ON_SALE, ItemModerationStatus.VISIBLE));
    }

    @GetMapping("/{id}")
    public ApiResponse<Item> detail(@PathVariable Long id) {
        return itemRepository.findById(id).filter(item -> item.getModerationStatus() == ItemModerationStatus.VISIBLE
                && item.getStatus() != ItemStatus.WITHDRAWN)
            .map(ApiResponse::ok)
            .orElseGet(() -> ApiResponse.fail("物品不存在"));
    }

    @GetMapping("/mine")
    public ApiResponse<List<SellerItemView>> mine() {
        return ApiResponse.ok(sellerInventory.list(actors.require().userId()));
    }

    @PostMapping
    public ApiResponse<SellerItemView> publish(@Valid @RequestBody ItemWriteRequest request) {
        Long sellerId = actors.require().userId();
        return ApiResponse.created(sellerInventory.publish(sellerId, request.toDraft()));
    }

    @PutMapping("/{id}")
    public ApiResponse<SellerItemView> revise(@PathVariable Long id,
                                               @Valid @RequestBody ItemWriteRequest request) {
        return ApiResponse.ok(sellerInventory.revise(actors.require().userId(), id, request.toDraft()));
    }

    @PostMapping("/{id}/seller-actions")
    public ApiResponse<SellerItemView> act(@PathVariable Long id,
                                            @Valid @RequestBody SellerActionRequest request) {
        return ApiResponse.ok(sellerInventory.act(actors.require().userId(), id, request.action()));
    }

    public record ItemWriteRequest(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 40) String category,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @Size(max = 1000) String description,
        @Size(max = 255) String imageUrl
    ) {
        SellerInventory.ItemDraft toDraft() {
            return new SellerInventory.ItemDraft(title, category, price, description, imageUrl);
        }
    }

    public record SellerActionRequest(@NotNull SellerItemAction action) {
    }
}
