package com.campus.secondhand.trading;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/internal/trading")
class InternalTradingController {
    private final TradingWorkflow trading;
    InternalTradingController(TradingWorkflow trading){this.trading=trading;}
    @GetMapping("/items/{itemId}/buyers/{buyerId}/active-inquiry")
    ResponseEntity<ApiResponse<Inquiry>> active(@PathVariable long itemId,@PathVariable long buyerId){
        return trading.activeInquiry(itemId,buyerId)
                .map(order->ResponseEntity.ok(ApiResponse.ok(new Inquiry(order.id(),order.status().name(),order.expiresAt().toString()))))
                .orElseGet(()->ResponseEntity.notFound().build());
    }
    @PostMapping("/marketplace-results") ApiResponse<TradingWorkflow.OrderView> result(@RequestBody TradingWorkflow.MarketplaceResult result){return ApiResponse.ok(trading.applyMarketplaceResult(result));}
    record Inquiry(long id,String status,String expiresAt){}
}
