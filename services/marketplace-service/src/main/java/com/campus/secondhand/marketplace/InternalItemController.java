package com.campus.secondhand.marketplace;
import java.nio.charset.StandardCharsets;
import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/internal/items") class InternalItemController {
 private final ItemRepository items; private final MarketplaceProperties props;
 InternalItemController(ItemRepository i,MarketplaceProperties p){items=i;props=p;}
 @GetMapping("/{id}/trade-snapshot") ApiResponse<Snapshot> snapshot(@PathVariable Long id,@RequestHeader(value="X-Internal-Service-Token",required=false) String token){
  if(token==null || !java.security.MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),props.internalServiceToken().getBytes(StandardCharsets.UTF_8))) throw new org.springframework.security.access.AccessDeniedException("internal only");
  return items.findById(id).map(i->ApiResponse.ok(new Snapshot(i.getId(),i.getTitle(),i.getPrice(),i.getImageUrl(),i.getSellerId(),i.getStatus().name(),i.getModerationStatus().name(),i.getReservedOrderId()))).orElseGet(()->ApiResponse.fail("商品不存在"));
 }
 record Snapshot(Long id,String title,java.math.BigDecimal price,String imageUrl,Long sellerId,String status,String moderationStatus,Long reservedOrderId){}
 @GetMapping("/{id}/governance-snapshot") ApiResponse<GovernanceSnapshot> governanceItem(@PathVariable Long id,@RequestHeader(value="X-Internal-Service-Token",required=false) String token){
  check(token); return items.findById(id).filter(i->i.getModerationStatus()==ItemModerationStatus.VISIBLE)
   .map(i->ApiResponse.ok(new GovernanceSnapshot(i.getId(),i.getSellerId(),i.getTitle(),"ITEM",true))).orElseGet(()->ApiResponse.fail("商品不存在或不可举报"));
 }
 private void check(String token){if(token==null || !java.security.MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),props.internalServiceToken().getBytes(StandardCharsets.UTF_8))) throw new org.springframework.security.access.AccessDeniedException("internal only");}
 record GovernanceSnapshot(Long targetId,Long reportedUserId,String summary,String targetType,boolean reportable){}
}
