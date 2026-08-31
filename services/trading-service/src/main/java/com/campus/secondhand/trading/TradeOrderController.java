package com.campus.secondhand.trading;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/orders")
class TradeOrderController {
    private final TradingWorkflow trading; private final CurrentActorService actors;
    TradeOrderController(TradingWorkflow trading,CurrentActorService actors){this.trading=trading;this.actors=actors;}
    @GetMapping ApiResponse<List<TradingWorkflow.OrderView>> list(Authentication auth){return ApiResponse.ok(trading.list(actors.require(auth)));}
    @GetMapping("/desk") ApiResponse<TradingWorkflow.Desk> desk(Authentication auth,
            @RequestParam(defaultValue="BUYING") TradingWorkflow.Perspective perspective,
            @RequestParam(defaultValue="ALL") TradingWorkflow.Stage stage){return ApiResponse.ok(trading.browse(actors.require(auth),perspective,stage));}
    @PostMapping ApiResponse<TradingWorkflow.OrderView> create(Authentication auth,@Valid @RequestBody CreateOrderRequest request){return ApiResponse.created(trading.requestPurchase(actors.require(auth),request.itemId()));}
    @PostMapping("/{id}/actions") ApiResponse<TradingWorkflow.OrderView> act(Authentication auth,@PathVariable long id,@Valid @RequestBody ActionRequest request){return ApiResponse.ok(trading.perform(actors.require(auth),id,request.action()));}
    record CreateOrderRequest(@NotNull Long itemId){} record ActionRequest(@NotNull OrderAction action){}
}
