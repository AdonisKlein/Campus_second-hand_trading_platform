package com.campus.secondhand.governance;

import com.fasterxml.jackson.databind.*;
import java.time.*;
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

@Configuration @ConditionalOnProperty(prefix="campus.governance",name="messaging-enabled",havingValue="true")
class GovernanceRabbitConfiguration {
    static final String EXCHANGE="campus.governance",RESULT_QUEUE="governance.action.results";
    @Bean Declarables governanceTopology(){DirectExchange exchange=new DirectExchange(EXCHANGE,true,false);Queue results=QueueBuilder.durable(RESULT_QUEUE).deadLetterExchange(EXCHANGE+".dlx").build();return new Declarables(exchange,results,BindingBuilder.bind(results).to(exchange).with("governance.results"));}
}

@Component @ConditionalOnProperty(prefix="campus.governance",name="messaging-enabled",havingValue="true")
class GovernanceOutboxPublisher {
    private final GovernanceOutboxRepository outbox;private final RabbitTemplate rabbit;private final Clock clock;private final ObjectMapper mapper=new ObjectMapper();
    GovernanceOutboxPublisher(GovernanceOutboxRepository outbox,RabbitTemplate rabbit,Clock clock){this.outbox=outbox;this.rabbit=rabbit;this.clock=clock;}
    @Scheduled(fixedDelayString="${campus.governance.publish-ms:1000}") @Transactional void publish()throws Exception{for(GovernanceOutboxEvent event:outbox.findByPublishedAtIsNullOrderById(PageRequest.of(0,100))){JsonNode payload=mapper.readTree(event.payload());String route="USER".equals(payload.path("targetType").asText())?"account.commands":"marketplace.commands";rabbit.convertAndSend(GovernanceRabbitConfiguration.EXCHANGE,route,event.payload());event.markPublished(LocalDateTime.now(clock));}}
}

@Component @ConditionalOnProperty(prefix="campus.governance",name="messaging-enabled",havingValue="true")
class GovernanceResultListener {
    private final ContentGovernance governance;private final GovernanceInboxRepository inbox;private final ObjectMapper mapper=new ObjectMapper();
    GovernanceResultListener(ContentGovernance governance,GovernanceInboxRepository inbox){this.governance=governance;this.inbox=inbox;}
    @RabbitListener(queues=GovernanceRabbitConfiguration.RESULT_QUEUE) void receive(Message payload)throws Exception{JsonNode n=mapper.readTree(payload.getBody());String eventId=n.path("eventId").asText();String correlationId=n.path("correlationId").asText();String commandEventId=n.path("commandEventId").asText(correlationId);try{governance.applyActionResult(new ContentGovernance.ActionResult(eventId,commandEventId,correlationId,n.path("type").asText(),n.path("reportId").asLong(),n.path("reason").isMissingNode()?null:n.path("reason").asText()));}catch(DataIntegrityViolationException duplicate){if(!inbox.existsById(eventId))throw duplicate;}}
}
