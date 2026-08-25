package com.campus.secondhand.order;

import com.campus.secondhand.common.ApiResponse;
import com.campus.secondhand.security.CurrentActorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class TradeOrderController {
    private final TradingService trading;
    private final CurrentActorService actors;

    public TradeOrderController(TradingService trading, CurrentActorService actors) {
        this.trading = trading;
        this.actors = actors;
    }

    @GetMapping
    public ApiResponse<List<OrderView>> list() {
        return ApiResponse.ok(trading.listOrders(actors.require().userId()));
    }

    @GetMapping("/desk")
    public ApiResponse<TradeDesk.Desk> desk(
            @RequestParam(defaultValue = "BUYING") TradeDesk.Perspective perspective,
            @RequestParam(defaultValue = "ALL") TradeDesk.Stage stage) {
        return ApiResponse.ok(trading.browse(actors.require().userId(), perspective, stage));
    }

    @PostMapping
    public ApiResponse<OrderView> create(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.created(trading.requestPurchase(actors.require().userId(), request.itemId()));
    }

    @PostMapping("/{id}/actions")
    public ApiResponse<OrderView> perform(@PathVariable Long id, @Valid @RequestBody OrderActionRequest request) {
        return ApiResponse.ok(trading.perform(actors.require().userId(), id, request.action()));
    }

    public record CreateOrderRequest(@NotNull Long itemId) {}
    public record OrderActionRequest(@NotNull OrderAction action) {}
}
