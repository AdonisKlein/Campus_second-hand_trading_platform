package com.campus.secondhand.trading;

import com.fasterxml.jackson.databind.JsonNode;import com.fasterxml.jackson.databind.ObjectMapper;import java.time.Clock;import java.time.LocalDateTime;import org.springframework.amqp.core.*;import org.springframework.amqp.rabbit.annotation.RabbitListener;import org.springframework.amqp.rabbit.core.RabbitTemplate;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.context.annotation.*;import org.springframework.data.domain.PageRequest;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.stereotype.Component;import org.springframework.transaction.annotation.Transactional;

@Configuration @ConditionalOnProperty(prefix="campus.trading",name="messaging-enabled",havingValue="true")
class TradingRabbitConfiguration{
 static final String EXCHANGE="campus.trading",COMMAND_QUEUE="marketplace.trade.commands",RESULT_QUEUE="trading.marketplace.results";
 @Bean Declarables tradingTopology(){DirectExchange exchange=new DirectExchange(EXCHANGE,true,false);Queue commands=QueueBuilder.durable(COMMAND_QUEUE).deadLetterExchange(EXCHANGE+".dlx").build();Queue results=QueueBuilder.durable(RESULT_QUEUE).deadLetterExchange(EXCHANGE+".dlx").build();return new Declarables(exchange,commands,results,BindingBuilder.bind(commands).to(exchange).with("marketplace.commands"),BindingBuilder.bind(results).to(exchange).with("trading.results"));}
}

@Component @ConditionalOnProperty(prefix="campus.trading",name="messaging-enabled",havingValue="true")
class TradingOutboxPublisher{
 private final OutboxEventRepository outbox;private final RabbitTemplate rabbit;private final Clock clock;
 TradingOutboxPublisher(OutboxEventRepository outbox,RabbitTemplate rabbit,Clock clock){this.outbox=outbox;this.rabbit=rabbit;this.clock=clock;}
 @Scheduled(fixedDelayString="${campus.trading.outbox-publish-ms:1000}") @Transactional void publish(){for(OutboxEvent event:outbox.findByPublishedAtIsNullOrderById(PageRequest.of(0,100))){rabbit.convertAndSend(TradingRabbitConfiguration.EXCHANGE,"marketplace.commands",event.getPayload());event.markPublished(LocalDateTime.now(clock));}}
}

@Component @ConditionalOnProperty(prefix="campus.trading",name="messaging-enabled",havingValue="true")
class MarketplaceResultListener{
 private final TradingWorkflow workflow;private final ObjectMapper mapper=new ObjectMapper();
 MarketplaceResultListener(TradingWorkflow workflow){this.workflow=workflow;}
 @RabbitListener(queues=TradingRabbitConfiguration.RESULT_QUEUE) void receive(Message payload)throws Exception{JsonNode node=mapper.readTree(payload.getBody());workflow.applyMarketplaceResult(new TradingWorkflow.MarketplaceResult(node.path("eventId").asText(),node.path("type").asText(),node.path("orderId").asLong(),node.path("itemId").asLong(),node.path("reason").isMissingNode()||node.path("reason").isNull()?null:node.path("reason").asText()));}
}
