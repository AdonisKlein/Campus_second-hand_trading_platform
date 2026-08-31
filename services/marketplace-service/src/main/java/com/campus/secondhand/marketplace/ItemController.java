package com.campus.secondhand.marketplace;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/items")
public class ItemController {
    private final ItemRepository items;
    private final SellerInventory inventory;
    private final ProductDetail productDetail;
    private final CurrentActorService actors;

    public ItemController(ItemRepository items, SellerInventory inventory, ProductDetail productDetail,
                          CurrentActorService actors) {
        this.items = items; this.inventory = inventory; this.productDetail = productDetail; this.actors = actors;
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductDetail.View> detail(@PathVariable Long id, Authentication authentication) {
        Long viewer = authentication != null && authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt
                ? actors.require(authentication).userId() : null;
        return productDetail.show(id, viewer).map(ApiResponse::ok).orElseGet(() -> ApiResponse.fail("商品不存在"));
    }

    @GetMapping
    public ApiResponse<List<Item>> list(@RequestParam(required = false) String category,
                                        @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(items.searchPublic(normalize(category), normalize(keyword),
                ItemStatus.ON_SALE, ItemModerationStatus.VISIBLE));
    }

    @GetMapping("/mine")
    public ApiResponse<List<SellerItemView>> mine(Authentication authentication) {
        return ApiResponse.ok(inventory.list(actors.require(authentication).userId()));
    }

    @PostMapping
    public ApiResponse<SellerItemView> publish(Authentication authentication,
                                                @Valid @RequestBody ItemWriteRequest request) {
        return ApiResponse.created(inventory.publish(actors.require(authentication).userId(), request.draft()));
    }

    @PutMapping("/{id}")
    public ApiResponse<SellerItemView> revise(Authentication authentication, @PathVariable Long id,
                                               @Valid @RequestBody ItemWriteRequest request) {
        return ApiResponse.ok(inventory.revise(actors.require(authentication).userId(), id, request.draft()));
    }

    @PostMapping("/{id}/seller-actions")
    public ApiResponse<SellerItemView> act(Authentication authentication, @PathVariable Long id,
                                            @Valid @RequestBody SellerActionRequest request) {
        return ApiResponse.ok(inventory.act(actors.require(authentication).userId(), id, request.action()));
    }

    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record ItemWriteRequest(
            @NotBlank @Size(max=120) String title,
            @NotBlank @Size(max=40) String category,
            @NotNull @DecimalMin("0.00") @Digits(integer=8, fraction=2) BigDecimal price,
            @Size(max=1000) String description,
            @Size(max=255) @Pattern(regexp="^$|^/media/product-images/[1-9]\\d*/[0-9a-fA-F-]{36}\\.(jpg|png)$") String imageUrl,
            @Pattern(regexp="^(学院路校区|沙河校区|大运村|其他校内区域)$") String region,
            @Size(max=4) Set<String> tags) {
        SellerInventory.ItemDraft draft() { return new SellerInventory.ItemDraft(title, category, price, description, imageUrl, region, tags); }
    }
    public record SellerActionRequest(@NotNull SellerItemAction action) { }
}
