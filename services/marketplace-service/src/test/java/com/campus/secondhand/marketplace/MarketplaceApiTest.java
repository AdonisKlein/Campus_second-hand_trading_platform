package com.campus.secondhand.marketplace;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.*;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketplaceApiTest {
    private static final String SECRET="test-jwt-secret-012345678901234567890123";
    @Autowired MockMvc mvc;
    @Autowired ItemRepository items;
    @Autowired SearchableUserProjectionRepository projections;
    @Autowired com.campus.secondhand.marketplace.message.MessageRepository messages;
    @Autowired UserProjectionUpdater projectionUpdater;
    @MockitoBean AccountPublicPort accounts;
    @MockitoBean TradingInquiryPort trading;

    @BeforeEach void clean(){messages.deleteAll();items.deleteAll();projections.deleteAll();}

    @Test void anonymousCanSearchProjectionWithoutCallingAccount() throws Exception {
        project(2,"seller","林同学","ACTIVE",120,1);
        Item item=item(2,"高等数学 教材",new BigDecimal("18.00"));
        mvc.perform(get("/api/search").param("q","高等 数学").param("sort","PRICE_ASC"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(item.getId()))
                .andExpect(jsonPath("$.data.items[0].sellerNickname").value("林同学"));
    }

    @Test void anonymousCanOpenPublicProductDetail() throws Exception {
        LocalDateTime active=LocalDateTime.of(2026,8,28,8,0);
        when(accounts.findPublic(2)).thenReturn(Optional.of(new AccountPublicPort.PublicAccount(
                2,"seller","林同学","学院路校区",120,"ACTIVE","STUDENT",active)));
        Item item=item(2,"高等数学教材",new BigDecimal("18.00"));
        mvc.perform(get("/api/items/"+item.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seller.displayName").value("林同学"))
                .andExpect(jsonPath("$.data.viewer.authenticated").value(false));
    }

    @Test void studentPublishesAsJwtSubjectAndCannotForgeSeller() throws Exception {
        when(accounts.findPublic(7)).thenReturn(Optional.of(new AccountPublicPort.PublicAccount(
                7,"student","学生","学院路校区",100,"ACTIVE","STUDENT",LocalDateTime.now())));
        mvc.perform(post("/api/items").header("Authorization","Bearer "+jwt(7,"STUDENT"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"台灯\",\"category\":\"生活\",\"price\":25,\"region\":\"学院路校区\",\"tags\":[\"可小刀\"],\"sellerId\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sellerId").value(7));
    }

    @Test void publicQuestionUsesItemSellerAndOwnerCanEdit() throws Exception {
        when(accounts.findPublic(2)).thenReturn(Optional.of(new AccountPublicPort.PublicAccount(
                2,"seller","卖家","学院路校区",100,"ACTIVE","STUDENT",LocalDateTime.now())));
        when(accounts.findPublic(7)).thenReturn(Optional.of(new AccountPublicPort.PublicAccount(
                7,"buyer","买家","沙河校区",100,"ACTIVE","STUDENT",LocalDateTime.now())));
        when(trading.activeInquiry(org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(Optional.empty());
        Item item=item(2,"耳机",new BigDecimal("68.00"));
        mvc.perform(post("/api/messages").header("Authorization","Bearer "+jwt(7,"STUDENT"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\":"+item.getId()+",\"content\":\"可以试听吗？\",\"receiverId\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.senderId").value(7))
                .andExpect(jsonPath("$.data.receiverId").value(2));
        mvc.perform(post("/api/messages").header("Authorization","Bearer "+jwt(2,"STUDENT"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\":"+item.getId()+",\"content\":\"自己顶一下\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false));
    }

    @Test void adminEndpointRequiresAdminRole() throws Exception {
        Item item=item(2,"待治理商品",BigDecimal.ONE);
        mvc.perform(put("/api/admin/items/"+item.getId()+"/status")
                .header("Authorization","Bearer "+jwt(7,"STUDENT"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REMOVED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/items/"+item.getId()+"/status")
                .header("Authorization","Bearer "+jwt(1,"ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REMOVED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.moderationStatus").value("REMOVED"));
    }

    @Test void mineAndWriteEndpointsRejectAnonymousVisitors() throws Exception {
        mvc.perform(get("/api/items/mine")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"台灯\",\"category\":\"生活\",\"price\":25}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void staleProfileEventCannotOverwriteNewProjection() throws Exception {
        project(8,"new-name","新昵称","ACTIVE",120,2);
        project(8,"old-name","旧昵称","DISABLED",10,1);
        mvc.perform(get("/api/search").param("scope","USERS").param("q","new-name"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.users[0].nickname").value("新昵称"));
        mvc.perform(get("/api/search").param("scope","USERS").param("q","old-name"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.users").isEmpty());
    }

    @Test void concurrentProfileEventsKeepHighestSourceVersion() throws Exception {
        project(9,"start","起点","ACTIVE",100,0);
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        Future<?> old=pool.submit(()->{await(start);project(9,"old","旧资料","DISABLED",10,1);});
        Future<?> latest=pool.submit(()->{await(start);project(9,"latest","最新资料","ACTIVE",130,2);});
        start.countDown();old.get(5,TimeUnit.SECONDS);latest.get(5,TimeUnit.SECONDS);pool.shutdownNow();
        SearchableUserProjection value=projections.findById(9L).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(value.getSourceVersion()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(value.getUsername()).isEqualTo("latest");
    }

    @Test void relevanceRanksExactTitleAheadOfNewerPartialTitle() throws Exception {
        project(2,"seller","卖家","ACTIVE",100,1);
        Item exact=item(2,"台灯",BigDecimal.TEN);item(2,"宿舍台灯",BigDecimal.ONE);
        mvc.perform(get("/api/search").param("q","台灯").param("sort","RELEVANCE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(exact.getId()));
    }

    private Item item(long seller,String title,BigDecimal price){Item value=new Item();value.setSellerId(seller);value.setTitle(title);value.setCategory("教材");value.setPrice(price);value.setRegion("学院路校区");return items.saveAndFlush(value);}
    private void project(long id,String username,String nickname,String status,int credit,long version){LocalDateTime now=LocalDateTime.of(2026,8,27,12,0);projectionUpdater.accept(new UserPublicProfileChanged("event-"+id+"-"+version,id,version,username,nickname,"学院路校区",credit,now,status,"STUDENT",now,now));}
    private void await(CountDownLatch latch){try{latch.await();}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}}
    private String jwt(long user,String role){Instant now=Instant.now();JwtClaimsSet claims=JwtClaimsSet.builder().issuer("campus-gateway").subject(String.valueOf(user)).claim("role",role).claim("email","u"+user+"@example.com").claim("auth_version",1).issuedAt(now).expiresAt(now.plusSeconds(60)).build();JwtEncoder encoder=NimbusJwtEncoder.withSecretKey(new SecretKeySpec(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256")).build();return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();}
}
