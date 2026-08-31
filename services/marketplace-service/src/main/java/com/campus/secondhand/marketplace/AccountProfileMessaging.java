package com.campus.secondhand.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.stereotype.Component;

@Configuration
@ConditionalOnProperty(prefix="campus.account-profile-events",name="enabled",havingValue="true")
class AccountProfileRabbitConfiguration {
    static final String EXCHANGE="campus.accounts", QUEUE="marketplace.account.profile";
    @Bean Declarables accountProfileTopology(){DirectExchange exchange=new DirectExchange(EXCHANGE,true,false);
        Queue queue=QueueBuilder.durable(QUEUE).build();
        return new Declarables(exchange,queue,BindingBuilder.bind(queue).to(exchange).with("profile.changed"));}
}

@Component
@ConditionalOnProperty(prefix="campus.account-profile-events",name="enabled",havingValue="true")
class AccountProfileListener {
    private final UserProjectionUpdater projections; private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    AccountProfileListener(UserProjectionUpdater projections){this.projections=projections;}
    @RabbitListener(queues=AccountProfileRabbitConfiguration.QUEUE)
    void receive(Message payload)throws Exception{projections.accept(mapper.readValue(payload.getBody(),UserPublicProfileChanged.class));}
}
