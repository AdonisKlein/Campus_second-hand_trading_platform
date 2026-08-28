package com.campus.secondhand.trading;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class TradingEventStore {
    private final OutboxEventRepository outbox;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Clock clock;
    TradingEventStore(OutboxEventRepository outbox,Clock clock){this.outbox=outbox;this.clock=clock;}
    void append(String type,long orderId,long itemId){
        String eventId=UUID.randomUUID().toString();
        Map<String,Object> event=new LinkedHashMap<>();
        event.put("eventId",eventId);event.put("correlationId",eventId);event.put("version",1);event.put("occurredAt",LocalDateTime.now(clock).toString());
        event.put("producer","trading-service");event.put("type",type);event.put("orderId",orderId);event.put("itemId",itemId);
        try{outbox.save(new OutboxEvent(eventId,type,mapper.writeValueAsString(event),LocalDateTime.now(clock)));}
        catch(JsonProcessingException error){throw new IllegalStateException("交易事件序列化失败",error);}
    }
}
