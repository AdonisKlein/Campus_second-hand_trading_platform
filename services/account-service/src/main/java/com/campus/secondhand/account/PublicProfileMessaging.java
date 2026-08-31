package com.campus.secondhand.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class PublicProfileEventStore {
    private final AccountOutboxRepository outbox;
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    PublicProfileEventStore(AccountOutboxRepository outbox) { this.outbox=outbox; }
    void record(User user) {
        long version=user.getPublicProfileVersion()+1; user.setPublicProfileVersion(version);
        String eventId=UUID.randomUUID().toString(); LocalDateTime now=LocalDateTime.now();
        Map<String,Object> body=new LinkedHashMap<>(); body.put("eventId",eventId); body.put("correlationId",CorrelationIdFilter.current());
        body.put("producer","account-service"); body.put("type","UserPublicProfileChanged"); body.put("userId",user.getId());
        body.put("version",version); body.put("username",user.getUsername()); body.put("nickname",user.getNickname());
        body.put("region",user.getCampusRegion()); body.put("creditScore",user.getCreditScore());
        body.put("lastActiveAt",user.getLastActiveAt()); body.put("status",user.getStatus()); body.put("role",user.getRole());
        body.put("createdAt",user.getCreatedAt()); body.put("occurredAt",now);
        try { outbox.save(new AccountOutboxEvent(eventId,"UserPublicProfileChanged",mapper.writeValueAsString(body))); }
        catch(Exception error){throw new IllegalStateException("用户公开资料事件序列化失败",error);}
    }
}

@Configuration
@ConditionalOnProperty(prefix="app.public-profile-events",name="enabled",havingValue="true")
class PublicProfileRabbitConfiguration {
    static final String EXCHANGE="campus.accounts";
    @Bean DirectExchange publicProfileExchange(){return new DirectExchange(EXCHANGE,true,false);}
}

@Component
@ConditionalOnProperty(prefix="app.public-profile-events",name="enabled",havingValue="true")
class PublicProfileOutboxPublisher {
    private final AccountOutboxRepository outbox; private final RabbitTemplate rabbit;
    PublicProfileOutboxPublisher(AccountOutboxRepository outbox,RabbitTemplate rabbit){this.outbox=outbox;this.rabbit=rabbit;}
    @Scheduled(fixedDelayString="${app.public-profile-events.publish-ms:500}") @Transactional
    void publish(){for(AccountOutboxEvent event:outbox.findByPublishedAtIsNullOrderById(PageRequest.of(0,100))){
        if(!"UserPublicProfileChanged".equals(event.getEventType()))continue;
        rabbit.convertAndSend(PublicProfileRabbitConfiguration.EXCHANGE,"profile.changed",event.getPayload());event.markPublished();
    }}
}
