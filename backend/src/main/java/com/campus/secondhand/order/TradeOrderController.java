package com.campus.secondhand.order;

import com.campus.secondhand.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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

    private final TradeOrderRepository orderRepository;

    public TradeOrderController(TradeOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @GetMapping
    public ApiResponse<List<TradeOrder>> list(@RequestParam Long userId) {
        return ApiResponse.ok(orderRepository.findByBuyerIdOrSellerIdOrderByCreatedAtDesc(userId, userId));
    }

    @PostMapping
    public ApiResponse<TradeOrder> create(@Valid @RequestBody CreateOrderRequest request) {
        TradeOrder order = new TradeOrder();
        order.setItemId(request.itemId());
        order.setBuyerId(request.buyerId());
        order.setSellerId(request.sellerId());
        return ApiResponse.created(orderRepository.save(order));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<TradeOrder> updateStatus(@PathVariable Long id, @RequestBody UpdateStatusRequest request) {
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

