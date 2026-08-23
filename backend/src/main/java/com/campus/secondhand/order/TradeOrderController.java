package com.campus.secondhand.order;

import com.campus.secondhand.common.ApiResponse;
import com.campus.secondhand.item.Item;
import com.campus.secondhand.item.ItemRepository;
import com.campus.secondhand.user.User;
import com.campus.secondhand.user.UserRepository;
import com.campus.secondhand.security.CurrentActorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/orders")
public class TradeOrderController {

    private static final Set<String> ALLOWED_STATUSES = Set.of("CREATED", "CONFIRMED", "COMPLETED", "CANCELLED");

    private final TradeOrderRepository orderRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final CurrentActorService actors;

    public TradeOrderController(TradeOrderRepository orderRepository, ItemRepository itemRepository,
                                UserRepository userRepository, CurrentActorService actors) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
        this.actors = actors;
    }

    @GetMapping
    public ApiResponse<List<OrderView>> list() {
        Long userId = actors.require().userId();
        List<OrderView> orders = orderRepository
            .findByBuyerIdOrSellerIdOrderByCreatedAtDesc(userId, userId)
            .stream()
            .map(this::toOrderView)
            .toList();

        return ApiResponse.ok(orders);
    }

    @PostMapping
    @Transactional
    public ApiResponse<TradeOrder> create(@Valid @RequestBody CreateOrderRequest request) {
        Long buyerId = actors.require().userId();
        var buyerOptional = userRepository.findById(buyerId);
        if (buyerOptional.isEmpty()) {
            return ApiResponse.fail("买家不存在");
        }
        if (isDisabled(buyerOptional.get())) {
            return ApiResponse.fail("账号已被管理员禁用");
        }

        var itemOptional = itemRepository.findLockedById(request.itemId());
        if (itemOptional.isEmpty()) {
            return ApiResponse.fail("物品不存在");
        }

        var item = itemOptional.get();

        Long sellerId = item.getSellerId();
        if (buyerId.equals(sellerId)) {
            return ApiResponse.fail("不能购买自己发布的物品");
        }
        if (!"ON_SALE".equals(item.getStatus()) || orderRepository.existsByItemIdAndStatusNot(request.itemId(), "CANCELLED")) {
            return ApiResponse.fail("物品已被下单或售出");
        }

        TradeOrder order = new TradeOrder();
        order.setItemId(request.itemId());
        order.setBuyerId(buyerId);
        order.setSellerId(sellerId);

        TradeOrder savedOrder = orderRepository.save(order);

        item.setStatus("SOLD");
        itemRepository.save(item);

        return ApiResponse.created(savedOrder);
    }

    @PutMapping("/{id}/status")
    @Transactional
    public ApiResponse<TradeOrder> updateStatus(@PathVariable Long id, @RequestBody UpdateStatusRequest request) {
        if (request.status() == null || !ALLOWED_STATUSES.contains(request.status())) {
            return ApiResponse.fail("订单状态不合法");
        }

        return orderRepository.findById(id)
            .map(order -> {
                Long actorId = actors.require().userId();
                if (!actorId.equals(order.getBuyerId()) && !actorId.equals(order.getSellerId())) {
                    return ApiResponse.<TradeOrder>fail("只能操作自己的订单");
                }
                if (!canTransition(order, actorId, request.status())) {
                    return ApiResponse.<TradeOrder>fail("当前身份不能执行该状态变更");
                }
                order.setStatus(request.status());
                if ("CANCELLED".equals(request.status())) {
                    itemRepository.findById(order.getItemId()).ifPresent(item -> {
                        item.setStatus("ON_SALE");
                        itemRepository.save(item);
                    });
                }
                return ApiResponse.ok(orderRepository.save(order));
            })
            .orElseGet(() -> ApiResponse.fail("订单不存在"));
    }

    private OrderView toOrderView(TradeOrder order) {
        Item item = itemRepository.findById(order.getItemId()).orElse(null);
        User buyer = userRepository.findById(order.getBuyerId()).orElse(null);
        User seller = userRepository.findById(order.getSellerId()).orElse(null);

        return new OrderView(
            order.getId(),
            order.getItemId(),
            item == null ? "" : item.getTitle(),
            item == null ? null : item.getPrice(),
            order.getBuyerId(),
            buyer == null ? "" : buyer.getNickname(),
            order.getSellerId(),
            seller == null ? "" : seller.getNickname(),
            order.getStatus()
        );
    }

    public record CreateOrderRequest(@NotNull Long itemId) {
    }

    public record UpdateStatusRequest(String status) {
    }

    public record OrderView(
        Long id,
        Long itemId,
        String itemTitle,
        java.math.BigDecimal itemPrice,
        Long buyerId,
        String buyerNickname,
        Long sellerId,
        String sellerNickname,
        String status
    ) {
    }

    private boolean isDisabled(User user) {
        return "DISABLED".equals(user.getStatus());
    }

    private boolean canTransition(TradeOrder order, Long actorId, String target) {
        if ("CANCELLED".equals(target)) {
            return Set.of("CREATED", "CONFIRMED").contains(order.getStatus());
        }
        if (actorId.equals(order.getSellerId())) {
            return "CREATED".equals(order.getStatus()) && "CONFIRMED".equals(target);
        }
        return actorId.equals(order.getBuyerId())
            && "CONFIRMED".equals(order.getStatus()) && "COMPLETED".equals(target);
    }
}
