package com.campus.secondhand.governance;

import jakarta.servlet.*;import jakarta.servlet.http.*;import java.io.IOException;import java.util.UUID;import java.util.regex.Pattern;import org.slf4j.*;import org.springframework.core.*;import org.springframework.core.annotation.Order;import org.springframework.stereotype.Component;import org.springframework.web.filter.OncePerRequestFilter;import org.springframework.web.reactive.function.client.*;import reactor.core.publisher.Mono;

@Component @Order(Ordered.HIGHEST_PRECEDENCE)
final class CorrelationIdFilter extends OncePerRequestFilter {
    static final String HEADER="X-Correlation-Id";private static final Pattern SAFE=Pattern.compile("[A-Za-z0-9._-]{1,64}");private static final Logger log=LoggerFactory.getLogger(CorrelationIdFilter.class);
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{String supplied=request.getHeader(HEADER);String id=supplied!=null&&SAFE.matcher(supplied).matches()?supplied:UUID.randomUUID().toString();long started=System.nanoTime();MDC.put("correlationId",id);response.setHeader(HEADER,id);try{chain.doFilter(request,response);}finally{log.info("request completed method={} path={} status={} durationMs={}",request.getMethod(),request.getRequestURI(),response.getStatus(),(System.nanoTime()-started)/1_000_000);MDC.remove("correlationId");}}
    static ExchangeFilterFunction propagate(){return ExchangeFilterFunction.ofRequestProcessor(request->Mono.just(ClientRequest.from(request).headers(headers->headers.set(HEADER,current())).build()));}
    static String current(){String id=MDC.get("correlationId");return id==null?UUID.randomUUID().toString():id;}
}
