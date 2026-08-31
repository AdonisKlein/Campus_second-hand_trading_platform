package com.campus.secondhand.trading;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradingWorkflowService implements TradingWorkflow {
    private static final List<OrderStatus> ACTIVE=List.of(OrderStatus.PURCHASE_REQUESTED,OrderStatus.WAITING_HANDOVER);
    private final TradeOrderRepository orders; private final AccountPort accounts; private final MarketplacePort marketplace;
    private final TradingEventStore events; private final InboxEventRepository inbox; private final Clock clock; private final TradingProperties properties;
    public TradingWorkflowService(TradeOrderRepository orders,AccountPort accounts,MarketplacePort marketplace,
                                  TradingEventStore events,InboxEventRepository inbox,Clock clock,TradingProperties properties){
        this.orders=orders;this.accounts=accounts;this.marketplace=marketplace;this.events=events;this.inbox=inbox;this.clock=clock;this.properties=properties;}

    @Override @Transactional
    public OrderView requestPurchase(CurrentActor actor,long itemId){
        AccountPort.AccountSnapshot buyer=requireActor(actor); MarketplacePort.ItemSnapshot item=marketplace.require(itemId);
        if(actor.userId()==item.sellerId())throw TradingException.conflict("SELF_PURCHASE","不能购买自己发布的商品");
        if(!item.publiclyTradable())throw TradingException.conflict("ITEM_UNAVAILABLE","商品当前不可提交购买意向");
        AccountPort.AccountSnapshot seller=accounts.requireActiveStudent(item.sellerId());
        Optional<TradeOrder> existing=orders.findFirstByItemIdAndBuyerIdAndStatusIn(itemId,actor.userId(),ACTIVE);
        if(existing.isPresent()){
            TradeOrder order=existing.get();
            if(order.getExpiresAt().isAfter(now())||order.getSagaState()!=SagaState.NONE)
                throw TradingException.conflict("DUPLICATE_ACTIVE_INTENT","你已经提交过购买意向");
            expireLocked(orders.findLockedById(order.getId()).orElseThrow(),false);
        }
        TradeOrder order=new TradeOrder();order.setItemId(item.id());order.setBuyerId(buyer.id());order.setSellerId(seller.id());
        order.setItemTitle(item.title());order.setItemPrice(item.price());order.setItemImageUrl(item.imageUrl());
        order.setBuyerNickname(buyer.displayName());order.setSellerNickname(seller.displayName());order.setExpiresAt(now().plusMinutes(properties.purchaseRequestMinutes()));
        order.setCreatedAt(now());order.setUpdatedAt(now());return view(orders.saveAndFlush(order),actor.userId());
    }

    @Override @Transactional
    public OrderView perform(CurrentActor actor,long orderId,OrderAction action){
        requireActor(actor);TradeOrder order=orders.findLockedById(orderId).orElseThrow(()->TradingException.notFound("订单不存在"));
        requireParticipant(order,actor.userId());
        if(ACTIVE.contains(order.getStatus())&&order.getSagaState()==SagaState.NONE&&!order.getExpiresAt().isAfter(now())){expireLocked(order,true);return view(order,actor.userId());}
        if(!allowedActions(order,actor.userId()).contains(action))throw TradingException.conflict("INVALID_TRANSITION","当前不能执行这个操作");
        switch(action){
            case ACCEPT->{
                MarketplacePort.ItemSnapshot item=marketplace.require(order.getItemId());
                if(!item.publiclyTradable())throw TradingException.conflict("ITEM_UNAVAILABLE","商品当前不能接受购买意向");
                if(orders.existsByItemIdAndSagaState(order.getItemId(),SagaState.RESERVE_PENDING))throw TradingException.conflict("RESERVATION_PENDING","商品正在为其他购买意向预留");
                order.setSagaState(SagaState.RESERVE_PENDING);order.setClosureReason("正在为买家预留商品");orders.saveAndFlush(order);
                events.append("ItemReservationRequested",order.getId(),order.getItemId());
            }
            case DECLINE->{order.setStatus(OrderStatus.DECLINED);order.setClosureReason("卖家暂未接受这次购买意向");orders.save(order);}
            case COMPLETE->{order.setSagaState(SagaState.SOLD_PENDING);order.setClosureReason("正在确认商品已售出");orders.saveAndFlush(order);events.append("ItemSoldRequested",order.getId(),order.getItemId());}
            case CANCEL->{
                String reason=actor.userId()==order.getBuyerId()?"买家取消":"卖家取消";
                if(order.getStatus()==OrderStatus.WAITING_HANDOVER)startRelease(order,OrderStatus.CANCELLED,reason);
                else{order.setStatus(OrderStatus.CANCELLED);order.setClosureReason(reason);orders.save(order);}
            }
        }
        return view(order,actor.userId());
    }

    @Override @Transactional(readOnly=true)
    public List<OrderView> list(CurrentActor actor){requireActor(actor);return orders.findByBuyerIdOrSellerIdOrderByCreatedAtDesc(actor.userId(),actor.userId()).stream().map(o->view(o,actor.userId())).toList();}

    @Override @Transactional(readOnly=true)
    public Desk browse(CurrentActor actor,Perspective perspective,Stage stage){
        requireActor(actor);Perspective p=perspective==null?Perspective.BUYING:perspective;Stage s=stage==null?Stage.ALL:stage;
        List<TradeOrder> source=p==Perspective.BUYING?orders.findByBuyerIdOrderByCreatedAtDesc(actor.userId()):orders.findBySellerIdOrderByCreatedAtDesc(actor.userId());
        long requests=source.stream().filter(o->effective(o)==OrderStatus.PURCHASE_REQUESTED).count();long handovers=source.stream().filter(o->effective(o)==OrderStatus.WAITING_HANDOVER).count();
        long actionable=source.stream().filter(o->!allowedActions(o,actor.userId()).isEmpty()).count();
        Map<Long,List<Entry>> grouped=new LinkedHashMap<>();
        source.stream().filter(o->belongs(o,s)).sorted(Comparator.comparing((TradeOrder o)->allowedActions(o,actor.userId()).isEmpty()).thenComparing(TradeOrder::getCreatedAt,Comparator.reverseOrder()))
            .forEach(o->grouped.computeIfAbsent(o.getItemId(),ignored->new ArrayList<>()).add(entry(o,actor.userId(),p)));
        List<ItemGroup> groups=grouped.values().stream().map(entries->{Entry first=entries.getFirst();return new ItemGroup(first.itemId(),first.itemTitle(),first.itemPrice(),List.copyOf(entries));}).toList();
        return new Desk(p,s,new Summary(requests,handovers,source.size()-requests-handovers,actionable),groups);
    }

    @Override @Transactional
    public OrderView applyMarketplaceResult(MarketplaceResult result){
        TradeOrder order=orders.findLockedById(result.orderId()).orElseThrow(()->TradingException.notFound("订单不存在"));
        if(inbox.existsById(result.eventId()))return view(order,order.getBuyerId());
        switch(result.type()){
            case "ItemReserved"->{if(order.getSagaState()==SagaState.RESERVE_PENDING){order.setStatus(OrderStatus.WAITING_HANDOVER);order.setSagaState(SagaState.NONE);order.setClosureReason(null);order.setExpiresAt(now().plusMinutes(properties.handoverMinutes()));closeOtherIntents(order);}}
            case "ItemReservationRejected"->{if(order.getSagaState()==SagaState.RESERVE_PENDING){order.setStatus(OrderStatus.DECLINED);order.setSagaState(SagaState.NONE);order.setClosureReason(result.reason()==null?"商品已被其他交易预留":result.reason());}}
            case "ItemReleased"->{if(order.getSagaState()==SagaState.RELEASE_PENDING){order.setStatus(order.getPendingFinalStatus());order.setPendingFinalStatus(null);order.setSagaState(SagaState.NONE);}}
            case "ItemSold"->{if(order.getSagaState()==SagaState.SOLD_PENDING){order.setStatus(OrderStatus.COMPLETED);order.setSagaState(SagaState.NONE);order.setClosureReason(null);}}
            case "ItemCommandRejected"->{order.setSagaState(SagaState.FAILED);order.setClosureReason(result.reason()==null?"商品状态同步失败，请联系管理员":result.reason());}
            default->throw new IllegalArgumentException("未知 Marketplace 结果事件");
        }
        orders.save(order);inbox.save(new InboxEvent(result.eventId(),result.type(),now()));return view(order,order.getBuyerId());
    }

    @Override @Transactional public boolean expire(long orderId){TradeOrder order=orders.findLockedById(orderId).orElse(null);if(order==null||!ACTIVE.contains(order.getStatus())||order.getSagaState()!=SagaState.NONE||order.getExpiresAt().isAfter(now()))return false;expireLocked(order,true);return true;}
    @Override @Transactional(readOnly=true) public Optional<OrderView> activeInquiry(long itemId,long buyerId){return orders.findFirstByItemIdAndBuyerIdAndStatusIn(itemId,buyerId,ACTIVE).filter(o->o.getExpiresAt().isAfter(now())).map(o->view(o,buyerId));}

    private AccountPort.AccountSnapshot requireActor(CurrentActor actor){if(!actor.student())throw TradingException.forbidden("只有学生用户可以交易");return accounts.requireActiveStudent(actor.userId());}
    private void requireParticipant(TradeOrder o,long actor){if(actor!=o.getBuyerId()&&actor!=o.getSellerId())throw TradingException.forbidden("当前不能查看或操作这笔订单");}
    private void startRelease(TradeOrder o,OrderStatus finalStatus,String reason){o.setSagaState(SagaState.RELEASE_PENDING);o.setPendingFinalStatus(finalStatus);o.setClosureReason(reason);orders.saveAndFlush(o);events.append("ItemReleaseRequested",o.getId(),o.getItemId());}
    private void expireLocked(TradeOrder o,boolean release){if(o.getStatus()==OrderStatus.WAITING_HANDOVER&&release)startRelease(o,OrderStatus.EXPIRED,"交易阶段已超过有效期");else{o.setStatus(OrderStatus.EXPIRED);o.setClosureReason("卖家未在有效期内回应");orders.save(o);}}
    private void closeOtherIntents(TradeOrder accepted){for(TradeOrder other:orders.findByItemIdAndStatus(accepted.getItemId(),OrderStatus.PURCHASE_REQUESTED)){if(other.getId().equals(accepted.getId()))continue;other.setStatus(other.getExpiresAt().isAfter(now())?OrderStatus.DECLINED:OrderStatus.EXPIRED);other.setClosureReason(other.getStatus()==OrderStatus.DECLINED?"卖家已选择其他买家":"卖家未在有效期内回应");orders.save(other);}}
    private List<OrderAction> allowedActions(TradeOrder o,long actor){if(o.getSagaState()!=SagaState.NONE||!o.getExpiresAt().isAfter(now()))return List.of();List<OrderAction> a=new ArrayList<>();if(o.getStatus()==OrderStatus.PURCHASE_REQUESTED){if(actor==o.getSellerId()){a.add(OrderAction.ACCEPT);a.add(OrderAction.DECLINE);}if(actor==o.getBuyerId())a.add(OrderAction.CANCEL);}else if(o.getStatus()==OrderStatus.WAITING_HANDOVER){if(actor==o.getBuyerId())a.add(OrderAction.COMPLETE);if(actor==o.getBuyerId()||actor==o.getSellerId())a.add(OrderAction.CANCEL);}return List.copyOf(a);}
    private OrderStatus effective(TradeOrder o){return ACTIVE.contains(o.getStatus())&&!o.getExpiresAt().isAfter(now())?OrderStatus.EXPIRED:o.getStatus();}
    private boolean belongs(TradeOrder o,Stage s){OrderStatus status=effective(o);return s==Stage.ALL||s==Stage.REQUESTS&&status==OrderStatus.PURCHASE_REQUESTED||s==Stage.HANDOVER&&status==OrderStatus.WAITING_HANDOVER||s==Stage.CLOSED&&!ACTIVE.contains(status);}
    private OrderView view(TradeOrder o,long actor){return new OrderView(o.getId(),o.getItemId(),o.getItemTitle(),o.getItemPrice(),o.getBuyerId(),o.getBuyerNickname(),o.getSellerId(),o.getSellerNickname(),effective(o),o.getExpiresAt(),o.getClosureReason(),o.getCreatedAt(),allowedActions(o,actor));}
    private Entry entry(TradeOrder o,long actor,Perspective p){long otherId=p==Perspective.BUYING?o.getSellerId():o.getBuyerId();String snapshot=p==Perspective.BUYING?o.getSellerNickname():o.getBuyerNickname();AccountPort.AccountSnapshot current=accounts.find(otherId).orElse(null);PublicCounterparty counterparty=new PublicCounterparty(otherId,current==null?snapshot:current.displayName(),current==null?null:current.campusRegion(),current==null?null:current.creditScore(),current==null?null:current.lastActiveAt());OrderStatus status=effective(o);return new Entry(o.getId(),o.getItemId(),o.getItemTitle(),o.getItemPrice(),o.getBuyerId(),o.getBuyerNickname(),o.getSellerId(),o.getSellerNickname(),counterparty,status,o.getExpiresAt(),o.getClosureReason(),o.getCreatedAt(),o.getUpdatedAt(),allowedActions(o,actor),timeline(o,status));}
    private List<TimelineStep> timeline(TradeOrder o,OrderStatus status){List<TimelineStep> result=new ArrayList<>();result.add(new TimelineStep("REQUESTED","提交购买意向","商品仍公开在售，等待卖家选择",StepState.COMPLETE,o.getCreatedAt()));if(status==OrderStatus.PURCHASE_REQUESTED){result.add(new TimelineStep("SELECTED","等待卖家回应","卖家可比较多位买家的购买意向",StepState.CURRENT,null));result.add(new TimelineStep("HANDOVER","校内当面交易","双方私聊约定公共地点",StepState.UPCOMING,null));}else if(status==OrderStatus.WAITING_HANDOVER){result.add(new TimelineStep("SELECTED","卖家已选定买家","商品已为本次交易预留",StepState.COMPLETE,o.getUpdatedAt()));result.add(new TimelineStep("HANDOVER","校内当面交易","验货前不要提前付款",StepState.CURRENT,null));}else if(status==OrderStatus.COMPLETED){result.add(new TimelineStep("COMPLETED","交易完成","买家已经确认取货",StepState.COMPLETE,o.getUpdatedAt()));}else result.add(new TimelineStep("CLOSED","交易已结束",o.getClosureReason()==null?"本次交易没有继续":o.getClosureReason(),StepState.STOPPED,o.getUpdatedAt()));return List.copyOf(result);}
    private LocalDateTime now(){return LocalDateTime.now(clock);}
}
