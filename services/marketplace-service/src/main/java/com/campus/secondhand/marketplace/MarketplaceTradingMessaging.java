package com.campus.secondhand.marketplace;

import com.fasterxml.jackson.databind.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Configuration @ConditionalOnProperty(prefix="campus.trading-events",name="enabled",havingValue="true")
class MarketplaceTradingRabbitConfiguration {
    static final String EXCHANGE="campus.trading",COMMAND_QUEUE="marketplace.trade.commands";
    @Bean Declarables marketplaceTradingTopology(){DirectExchange exchange=new DirectExchange(EXCHANGE,true,false);Queue commands=QueueBuilder.durable(COMMAND_QUEUE).deadLetterExchange(EXCHANGE+".dlx").build();return new Declarables(exchange,commands,BindingBuilder.bind(commands).to(exchange).with("marketplace.commands"));}
}
@Configuration @ConditionalOnProperty(prefix="campus.governance-events",name="enabled",havingValue="true")
class MarketplaceGovernanceRabbitConfiguration {
    static final String EXCHANGE="campus.governance",COMMAND_QUEUE="marketplace.governance.commands";
    @Bean Declarables marketplaceGovernanceTopology(){DirectExchange exchange=new DirectExchange(EXCHANGE,true,false);Queue commands=QueueBuilder.durable(COMMAND_QUEUE).deadLetterExchange(EXCHANGE+".dlx").build();return new Declarables(exchange,commands,BindingBuilder.bind(commands).to(exchange).with("marketplace.commands"));}
}
@Component @ConditionalOnProperty(prefix="campus.trading-events",name="enabled",havingValue="true")
class MarketplaceCommandListener {
    private final TradingEventHandler handler;private final ObjectMapper mapper=new ObjectMapper();MarketplaceCommandListener(TradingEventHandler handler){this.handler=handler;}
    @RabbitListener(queues=MarketplaceTradingRabbitConfiguration.COMMAND_QUEUE)void receive(String payload)throws Exception{JsonNode n=mapper.readTree(payload);handler.handle(n.path("eventId").asText(),n.path("type").asText(),n.path("itemId").asLong(),n.path("orderId").asLong());}
}
@Component @ConditionalOnProperty(prefix="campus.governance-events",name="enabled",havingValue="true")
class MarketplaceGovernanceCommandListener {
    private final TradingEventHandler handler;private final ObjectMapper mapper=new ObjectMapper();MarketplaceGovernanceCommandListener(TradingEventHandler handler){this.handler=handler;}
    @RabbitListener(queues=MarketplaceGovernanceRabbitConfiguration.COMMAND_QUEUE)void receive(String payload)throws Exception{JsonNode event=mapper.readTree(payload);String eventId=event.path("eventId").asText();try{handler.handleGovernance(event);}catch(DataIntegrityViolationException duplicate){if(!handler.processed(eventId))throw duplicate;}}
}
@Component @ConditionalOnProperty(prefix="campus.trading-events",name="enabled",havingValue="true")
class MarketplaceOutboxPublisher {
    private final MarketplaceOutboxRepository outbox;private final RabbitTemplate rabbit;MarketplaceOutboxPublisher(MarketplaceOutboxRepository outbox,RabbitTemplate rabbit){this.outbox=outbox;this.rabbit=rabbit;}
    @Scheduled(fixedDelayString="${campus.trading-events.publish-ms:1000}")@Transactional void publish(){for(MarketplaceOutboxEvent event:outbox.findByPublishedAtIsNullOrderById(PageRequest.of(0,100))){if(event.governance())continue;rabbit.convertAndSend(MarketplaceTradingRabbitConfiguration.EXCHANGE,"trading.results",event.payload);event.markPublished();}}
}
@Component @ConditionalOnProperty(prefix="campus.governance-events",name="enabled",havingValue="true")
class MarketplaceGovernanceOutboxPublisher {
    private final MarketplaceOutboxRepository outbox;private final RabbitTemplate rabbit;MarketplaceGovernanceOutboxPublisher(MarketplaceOutboxRepository outbox,RabbitTemplate rabbit){this.outbox=outbox;this.rabbit=rabbit;}
    @Scheduled(fixedDelayString="${campus.governance-events.publish-ms:1000}")@Transactional void publish(){for(MarketplaceOutboxEvent event:outbox.findByPublishedAtIsNullOrderById(PageRequest.of(0,100))){if(!event.governance())continue;rabbit.convertAndSend(MarketplaceGovernanceRabbitConfiguration.EXCHANGE,"governance.results",event.payload);event.markPublished();}}
}
