package com.campus.secondhand.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;import jakarta.persistence.*;import java.time.LocalDateTime;import java.util.*;import org.springframework.data.domain.Pageable;import org.springframework.data.jpa.repository.JpaRepository;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import com.campus.secondhand.marketplace.message.MessageRepository;

@Entity @Table(name="marketplace_event_inbox") class MarketplaceInboxEvent{@Id @Column(name="event_id",length=80)String eventId;@Column(name="event_type",nullable=false,length=120)String eventType;@Column(name="processed_at",nullable=false)LocalDateTime processedAt;MarketplaceInboxEvent(){}MarketplaceInboxEvent(String id,String type){eventId=id;eventType=type;processedAt=LocalDateTime.now();}}
interface MarketplaceInboxRepository extends JpaRepository<MarketplaceInboxEvent,String>{}
@Entity @Table(name="marketplace_event_outbox") class MarketplaceOutboxEvent{@Id@GeneratedValue(strategy=GenerationType.IDENTITY)Long id;@Column(name="event_id",nullable=false,unique=true,length=80)String eventId;@Column(name="event_type",nullable=false,length=120)String eventType;@Column(nullable=false,columnDefinition="TEXT")String payload;@Column(name="created_at",nullable=false)LocalDateTime createdAt=LocalDateTime.now();@Column(name="published_at")LocalDateTime publishedAt;MarketplaceOutboxEvent(){}MarketplaceOutboxEvent(String id,String type,String payload){eventId=id;eventType=type;this.payload=payload;}void markPublished(){publishedAt=LocalDateTime.now();}boolean governance(){return eventType.startsWith("GovernanceAction");}}
interface MarketplaceOutboxRepository extends JpaRepository<MarketplaceOutboxEvent,Long>{List<MarketplaceOutboxEvent>findByPublishedAtIsNullOrderById(Pageable page);}

@Service class TradingEventHandler{
 private final ItemRepository items;private final MessageRepository messages;private final MarketplaceInboxRepository inbox;private final MarketplaceOutboxRepository outbox;private final ObjectMapper mapper=new ObjectMapper();
 TradingEventHandler(ItemRepository items,MessageRepository messages,MarketplaceInboxRepository inbox,MarketplaceOutboxRepository outbox){this.items=items;this.messages=messages;this.inbox=inbox;this.outbox=outbox;}
 @Transactional public void handle(String eventId,String type,Long itemId,Long orderId){if(inbox.existsById(eventId))return;Item item=items.findLockedById(itemId).orElse(null);String result;String reason=null;
  if(item==null){result="ItemCommandRejected";reason="商品不存在";}
  else switch(type){
   case "ItemReservationRequested"->{if(item.getStatus()==ItemStatus.ON_SALE&&item.getModerationStatus()==ItemModerationStatus.VISIBLE){item.setStatus(ItemStatus.RESERVED);item.setReservedOrderId(orderId);result="ItemReserved";}else if(item.getStatus()==ItemStatus.RESERVED&&orderId.equals(item.getReservedOrderId()))result="ItemReserved";else{result="ItemReservationRejected";reason="商品已经不可预留";}}
   case "ItemReleaseRequested"->{if(item.getStatus()==ItemStatus.RESERVED&&orderId.equals(item.getReservedOrderId())){item.setStatus(ItemStatus.ON_SALE);item.setReservedOrderId(null);result="ItemReleased";}else{result="ItemCommandRejected";reason="预留关系不匹配";}}
   case "ItemSoldRequested"->{if(item.getStatus()==ItemStatus.RESERVED&&orderId.equals(item.getReservedOrderId())){item.setStatus(ItemStatus.SOLD);item.setReservedOrderId(null);result="ItemSold";}else{result="ItemCommandRejected";reason="预留关系不匹配";}}
   default->throw new IllegalArgumentException("未知交易事件");
  }
  inbox.save(new MarketplaceInboxEvent(eventId,type));String resultId=eventId+":result";Map<String,Object> payload=new LinkedHashMap<>();payload.put("eventId",resultId);payload.put("correlationId",eventId);payload.put("version",1);payload.put("occurredAt",LocalDateTime.now().toString());payload.put("producer","marketplace-service");payload.put("type",result);payload.put("orderId",orderId);payload.put("itemId",itemId);if(reason!=null)payload.put("reason",reason);
  try{outbox.save(new MarketplaceOutboxEvent(resultId,result,mapper.writeValueAsString(payload)));}catch(Exception error){throw new IllegalStateException("商品交易结果序列化失败",error);}
 }
 @Transactional public void handleGovernance(com.fasterxml.jackson.databind.JsonNode n){
  String eventId=n.path("eventId").asText();String action=n.path("action").asText();if(inbox.existsById(eventId))return;
  Long targetId=n.path("targetId").asLong();String result;String reason=null;
  if("REMOVE_ITEM".equals(action)){var item=items.findLockedById(targetId).orElse(null);if(item==null){result="GovernanceActionFailed";reason="商品不存在";}else if(item.getModerationStatus()==ItemModerationStatus.REMOVED){result="GovernanceActionApplied";}else{item.setModerationStatus(ItemModerationStatus.REMOVED);items.save(item);result="GovernanceActionApplied";}}
  else if("REMOVE_MESSAGE".equals(action)){if(messages.existsById(targetId)){messages.deleteById(targetId);result="GovernanceActionApplied";}else{result="GovernanceActionFailed";reason="留言不存在";}}
  else {result="GovernanceActionFailed";reason="Marketplace 不支持该治理动作";}
  inbox.saveAndFlush(new MarketplaceInboxEvent(eventId,n.path("type").asText()));
  Map<String,Object> p=new LinkedHashMap<>();p.put("eventId",eventId+":result");p.put("correlationId",n.path("correlationId").asText(eventId));p.put("version",1);p.put("occurredAt",LocalDateTime.now().toString());p.put("producer","marketplace-service");p.put("type",result);p.put("action",action);p.put("reportId",n.path("reportId").asLong());p.put("targetId",targetId);if(reason!=null)p.put("reason",reason);
  try{outbox.save(new MarketplaceOutboxEvent(eventId+":result",result,mapper.writeValueAsString(p)));}catch(Exception e){throw new IllegalStateException("治理结果序列化失败",e);}
 }
 boolean processed(String eventId){return eventId!=null&&inbox.existsById(eventId);}
}
