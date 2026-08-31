package com.campus.secondhand.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Configuration
@ConditionalOnProperty(prefix="app.governance-events", name="enabled", havingValue="true")
class GovernanceRabbitConfiguration {
 static final String EXCHANGE="campus.governance", QUEUE="account.governance.commands";
 @Bean Declarables governanceTopology() { DirectExchange e=new DirectExchange(EXCHANGE,true,false); Queue q=QueueBuilder.durable(QUEUE).deadLetterExchange(EXCHANGE+".dlx").build(); return new Declarables(e,q,BindingBuilder.bind(q).to(e).with("account.commands")); }
}

@Component
@ConditionalOnProperty(prefix="app.governance-events", name="enabled", havingValue="true")
class GovernanceCommandListener {
 private final GovernanceActionHandler handler; private final ObjectMapper mapper=new ObjectMapper();
 GovernanceCommandListener(GovernanceActionHandler h) { handler=h; }
 @RabbitListener(queues=GovernanceRabbitConfiguration.QUEUE)
 void receive(Message body) throws Exception {
  JsonNode n=mapper.readTree(body.getBody()); String eventId=n.path("eventId").asText();
  try { handler.handle(eventId, n.path("type").asText(), n.path("payload").isMissingNode()?n:n.path("payload")); }
  catch (DataIntegrityViolationException duplicate) { if (!handler.processed(eventId)) throw duplicate; }
 }
}

@Component
@ConditionalOnProperty(prefix="app.governance-events", name="enabled", havingValue="true")
class GovernanceOutboxPublisher {
 private final AccountOutboxRepository outbox; private final RabbitTemplate rabbit;
 GovernanceOutboxPublisher(AccountOutboxRepository o, RabbitTemplate r) { outbox=o; rabbit=r; }
 @Scheduled(fixedDelayString="${app.governance-events.publish-ms:1000}") @Transactional
 void publish() { for (AccountOutboxEvent e: outbox.findByPublishedAtIsNullOrderById(PageRequest.of(0,100))) { if("UserPublicProfileChanged".equals(e.getEventType()))continue; rabbit.convertAndSend(GovernanceRabbitConfiguration.EXCHANGE,"governance.results",e.getPayload()); e.markPublished(); } }
}
