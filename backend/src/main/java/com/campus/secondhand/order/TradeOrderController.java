package com.campus.secondhand.order;

import com.campus.secondhand.common.ApiResponse;
import com.campus.secondhand.item.ItemRepository;
import com.campus.secondhand.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
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
@RequestMapping("/orders")
public class TradeOrderController {

    private static final Set<String> ALLOWED_STATUSES = Set.of("CREATED", "CONFIRMED", "COMPLETED", "CANCELLED");

    private final TradeOrderRepository orderRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;

    public TradeOrderController(TradeOrderRepository orderRepository, ItemRepository itemRepository, UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ApiResponse<List<TradeOrder>> list(@RequestParam Long userId) {
        return ApiResponse.ok(orderRepository.findByBuyerIdOrSellerIdOrderByCreatedAtDesc(userId, userId));
    }

    @PostMapping
    public ApiResponse<TradeOrder> create(@Valid @RequestBody CreateOrderRequest request) {
        if (!userRepository.existsById(request.buyerId())) {
            return ApiResponse.fail("买家不存在");
        }
        if (!userRepository.existsById(request.sellerId())) {
            return ApiResponse.fail("卖家不存在");
        }
        var itemOptional = itemRepository.findById(request.itemId());
        if (itemOptional.isEmpty()) {
            return ApiResponse.fail("物品不存在");
        }
        var item = itemOptional.get();
        if (!item.getSellerId().equals(request.sellerId())) {
            return ApiResponse.fail("卖家与物品信息不匹配");
        }
        if (request.buyerId().equals(request.sellerId())) {
            return ApiResponse.fail("不能购买自己发布的物品");
        }
        if (!"ON_SALE".equals(item.getStatus()) || orderRepository.existsByItemIdAndStatusNot(request.itemId(), "CANCELLED")) {
            return ApiResponse.fail("物品已被下单或售出");
        }

        TradeOrder order = new TradeOrder();
        order.setItemId(request.itemId());
        order.setBuyerId(request.buyerId());
        order.setSellerId(request.sellerId());
        TradeOrder savedOrder = orderRepository.save(order);
        item.setStatus("SOLD");
        itemRepository.save(item);
        return ApiResponse.created(savedOrder);
    }

    @PutMapping("/{id}/status")
    public ApiResponse<TradeOrder> updateStatus(@PathVariable Long id, @RequestBody UpdateStatusRequest request) {
        if (request.status() == null || !ALLOWED_STATUSES.contains(request.status())) {
            return ApiResponse.fail("订单状态不合法");
        }
        return orderRepository.findById(id)
            .map(order -> {
                order.setStatus(request.status());
                return ApiResponse.ok(orderRepository.save(order));
            })
            .orElseGet(() -> ApiResponse.fail("订单不存在"));
    }

    public record CreateOrderRequest(@NotNull Long itemId, @NotNull Long buyerId, @NotNull Long sellerId) {
    }

    public record UpdateStatusRequest(String status) {
    }
}
