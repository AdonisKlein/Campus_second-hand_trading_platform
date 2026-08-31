package com.campus.secondhand.marketplace;

import com.campus.secondhand.marketplace.message.Message;
import com.campus.secondhand.marketplace.message.MessageRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/admin")
public class MarketplaceAdminController {
    private final ItemRepository items; private final MessageRepository messages;
    public MarketplaceAdminController(ItemRepository items,MessageRepository messages){this.items=items;this.messages=messages;}
    @GetMapping("/items") public ApiResponse<List<Item>> items(){return ApiResponse.ok(items.findAll());}
    @Transactional @PutMapping("/items/{id}/status") public ApiResponse<Item> moderate(@PathVariable Long id,@Valid @RequestBody ModerationRequest request){Item item=items.findLockedById(id).orElseThrow(()->new MarketplaceException(org.springframework.http.HttpStatus.NOT_FOUND,"ITEM_NOT_FOUND","商品不存在"));item.setModerationStatus(request.status());return ApiResponse.ok(items.save(item));}
    @GetMapping("/messages") public ApiResponse<List<Message>> messages(){return ApiResponse.ok(messages.findAll());}
    @DeleteMapping("/messages/{id}") public ApiResponse<String> deleteMessage(@PathVariable Long id){if(!messages.existsById(id))throw new MarketplaceException(org.springframework.http.HttpStatus.NOT_FOUND,"MESSAGE_NOT_FOUND","留言不存在");messages.deleteById(id);return ApiResponse.ok("删除成功");}
    public record ModerationRequest(@NotNull ItemModerationStatus status){}
}
