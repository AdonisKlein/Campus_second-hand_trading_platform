package com.campus.secondhand.trading;

import io.netty.channel.ChannelOption;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

public final class InternalWebClients {
    private InternalWebClients() {}

    public static WebClient create(WebClient.Builder builder, String baseUrl, int connectTimeoutMs) {
        return builder.clone()
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)))
                .baseUrl(baseUrl)
                .build();
    }
}
