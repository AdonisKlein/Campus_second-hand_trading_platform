package com.campus.secondhand.governance;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import java.util.*;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.*;
import reactor.netty.http.client.HttpClient;

abstract class OwnedRemoteAdapter {
    private final WebClient client;
    private final GovernanceProperties properties;
    OwnedRemoteAdapter(WebClient.Builder builder,GovernanceProperties properties,String baseUrl){
        this.client=builder.clone().clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,300))).baseUrl(baseUrl).build();this.properties=properties;
    }
    Map<?,?> get(String path,Object... values){
        try{return client.get().uri(path,values).header("X-Internal-Service-Token",properties.internalServiceToken())
                .retrieve().bodyToMono(Map.class).timeout(Duration.ofMillis(800)).retry(1).block();}
        catch(WebClientResponseException.NotFound ignored){return Map.of();}
        catch(RuntimeException error){throw GovernanceException.unavailable();}
    }
}

@Component
class HttpAccountGovernanceAdapter extends OwnedRemoteAdapter implements AccountGovernancePort {
    HttpAccountGovernanceAdapter(WebClient.Builder b,GovernanceProperties p){super(b,p,p.accountUri());}
    public Optional<AccountSnapshot> find(long id){Object raw=get("/internal/users/{id}/public",id).get("data");if(!(raw instanceof Map<?,?> data))return Optional.empty();return Optional.of(new AccountSnapshot(number(data,"id"),text(data,"username"),text(data,"nickname"),text(data,"status"),text(data,"role")));}
    private long number(Map<?,?> data,String key){return ((Number)data.get(key)).longValue();}private String text(Map<?,?> data,String key){return data.get(key)==null?null:String.valueOf(data.get(key));}
}

@Component
class HttpReportTargetAdapter extends OwnedRemoteAdapter implements ReportTargetPort {
    private final AccountGovernancePort accounts;
    HttpReportTargetAdapter(WebClient.Builder b,GovernanceProperties p,AccountGovernancePort accounts){super(b,p,p.marketplaceUri());this.accounts=accounts;}
    public TargetSnapshot resolve(ReportTargetType type,long id){
        if(type==ReportTargetType.USER){AccountGovernancePort.AccountSnapshot user=accounts.find(id).filter(AccountGovernancePort.AccountSnapshot::activeStudent).orElseThrow(()->GovernanceException.notFound("无法举报该内容"));return new TargetSnapshot(id,id,user.displayName(),type,true);}
        String segment=type==ReportTargetType.ITEM?"items":"messages";Object raw=get("/internal/"+segment+"/{id}/governance-snapshot",id).get("data");if(!(raw instanceof Map<?,?> data))throw GovernanceException.notFound("无法举报该内容");
        boolean reportable=Boolean.TRUE.equals(data.get("reportable"));if(!reportable)throw GovernanceException.notFound("无法举报该内容");
        return new TargetSnapshot(((Number)data.get("targetId")).longValue(),((Number)data.get("reportedUserId")).longValue(),String.valueOf(data.get("summary")),ReportTargetType.valueOf(String.valueOf(data.get("targetType"))),true);
    }
}
