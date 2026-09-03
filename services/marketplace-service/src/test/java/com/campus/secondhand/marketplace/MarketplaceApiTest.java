package com.campus.secondhand.marketplace;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.*;
import javax.crypto.spec.SecretKeySpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.mock.web.MockMultipartFile;
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
    private final ObjectMapper objectMapper=new ObjectMapper();
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

    @Test void anonymousBlankSearchFallsBackToNewestItems() throws Exception {
        project(2, "seller", "林同学", "ACTIVE", 120, 1);
        item(2, "高等数学 教材", new BigDecimal("18.00"));

        mvc.perform(get("/api/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("ITEMS"))
                .andExpect(jsonPath("$.data.items[0].title").value("高等数学 教材"));
    }

    @Test void anonymousCanOpenPublicProductDetail() throws Exception {
        LocalDateTime active=LocalDateTime.of(2026,8,28,8,0);
        when(accounts.findPublic(2)).thenReturn(Optional.of(new AccountPublicPort.PublicAccount(
                2,"seller","林同学","学院路校区",120,"ACTIVE","STUDENT",active)));
        Item item=item(2,"高等数学教材",new BigDecimal("18.00"));
        mvc.perform(get("/api/items/"+item.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seller.displayName").value("林同学"))
                .andExpect(jsonPath("$.data.viewer.authenticated").value(false));
        mvc.perform(get("/api/items/999999")).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
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
        mvc.perform(put("/api/items/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/items/1/seller-actions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/media/product-images")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/messages").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/messages/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/messages/1")).andExpect(status().isUnauthorized());
        String student="Bearer "+jwt(7,"STUDENT");
        mvc.perform(get("/api/admin/items").header("Authorization",student)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/items/1/status").header("Authorization",student)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REMOVED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/messages").header("Authorization",student)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/messages/1").header("Authorization",student)).andExpect(status().isForbidden());
    }

    @Test void productImageUploadRejectsNonMultipartRequestAsBadRequest() throws Exception {
        mvc.perform(post("/api/media/product-images")
                .header("Authorization", "Bearer " + jwt(7, "STUDENT"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
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

    @Test void publicItemListFiltersAndHidesUnavailableInventory() throws Exception {
        project(2,"seller","卖家","ACTIVE",100,1);
        Item visible=item(2,"高等数学教材",new BigDecimal("18.00"));
        Item withdrawn=item(2,"高等数学旧版",new BigDecimal("8.00"));
        withdrawn.setStatus(ItemStatus.WITHDRAWN);items.saveAndFlush(withdrawn);
        Item removed=item(2,"高等数学答案",new BigDecimal("5.00"));
        removed.setModerationStatus(ItemModerationStatus.REMOVED);items.saveAndFlush(removed);

        mvc.perform(get("/api/items").param("category","教材").param("keyword","高等数学"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(visible.getId()));
    }

    @Test void sellerCanListReviseWithdrawAndRelistOwnItem() throws Exception {
        project(7,"student","学生","ACTIVE",100,1);
        when(accounts.findPublic(7)).thenReturn(Optional.of(new AccountPublicPort.PublicAccount(
                7,"student","学生","学院路校区",100,"ACTIVE","STUDENT",LocalDateTime.now())));
        Item existing=item(7,"旧标题",new BigDecimal("20.00"));
        String auth="Bearer "+jwt(7,"STUDENT");

        mvc.perform(get("/api/items/mine").header("Authorization",auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(existing.getId()));
        mvc.perform(put("/api/items/"+existing.getId()).header("Authorization",auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"新标题\",\"category\":\"教材\",\"price\":21,\"region\":\"学院路校区\",\"tags\":[\"可小刀\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("新标题"));
        mvc.perform(post("/api/items/"+existing.getId()+"/seller-actions").header("Authorization",auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"RELIST\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(post("/api/items/"+existing.getId()+"/seller-actions").header("Authorization",auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"WITHDRAW\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("WITHDRAWN"));
        mvc.perform(post("/api/items/"+existing.getId()+"/seller-actions").header("Authorization",auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"RELIST\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ON_SALE"));

        Item stored=items.findById(existing.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(stored.getTitle()).isEqualTo("新标题");
        org.assertj.core.api.Assertions.assertThat(stored.getStatus()).isEqualTo(ItemStatus.ON_SALE);
    }

    @Test void invalidItemWriteAndForeignSellerEditAreRejectedWithoutChangingDatabase() throws Exception {
        Item existing=item(2,"原商品",new BigDecimal("20.00"));
        mvc.perform(post("/api/items").header("Authorization","Bearer "+jwt(7,"STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"category\":\"教材\",\"price\":-1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(put("/api/items/"+existing.getId()).header("Authorization","Bearer "+jwt(7,"STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"越权修改\",\"category\":\"教材\",\"price\":20,\"region\":\"学院路校区\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false));
        org.assertj.core.api.Assertions.assertThat(items.findAll()).singleElement()
                .extracting(Item::getTitle).isEqualTo("原商品");
    }

    @Test void studentCanUploadAndLoadValidatedPng() throws Exception {
        byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        MockMultipartFile file=new MockMultipartFile("file","lamp.png","image/png",png);
        String response=mvc.perform(multipart("/api/media/product-images")
                        .file(file).header("Authorization","Bearer "+jwt(7,"STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.contentType").value("image/png"))
                .andExpect(jsonPath("$.data.width").value(1)).andExpect(jsonPath("$.data.height").value(1))
                .andReturn().getResponse().getContentAsString();
        JsonNode data=objectMapper.readTree(response).path("data");
        String filename=data.path("url").asText().substring(data.path("url").asText().lastIndexOf('/')+1);

        byte[] stored=mvc.perform(get("/api/media/product-images/7/"+filename))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andReturn().getResponse().getContentAsByteArray();
        org.assertj.core.api.Assertions.assertThat(stored).hasSize(data.path("size").asInt())
                .startsWith((byte)0x89,(byte)0x50,(byte)0x4e,(byte)0x47);
        mvc.perform(get("/api/media/product-images/7/00000000-0000-0000-0000-000000000000.png"))
                .andExpect(status().isNotFound());
    }

    @Test void messageCrudAndAdminQueuesPersistExpectedState() throws Exception {
        when(accounts.findPublic(2)).thenReturn(Optional.of(new AccountPublicPort.PublicAccount(
                2,"seller","卖家","学院路校区",100,"ACTIVE","STUDENT",LocalDateTime.now())));
        when(accounts.findPublic(7)).thenReturn(Optional.of(new AccountPublicPort.PublicAccount(
                7,"buyer","买家","沙河校区",100,"ACTIVE","STUDENT",LocalDateTime.now())));
        when(trading.activeInquiry(org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(Optional.empty());
        Item product=item(2,"耳机",new BigDecimal("68.00"));
        String created=mvc.perform(post("/api/messages").header("Authorization","Bearer "+jwt(7,"STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":"+product.getId()+",\"content\":\"可以试听吗？\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long messageId=objectMapper.readTree(created).path("data").path("id").asLong();

        mvc.perform(get("/api/messages/item/"+product.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].content").value("可以试听吗？"));
        mvc.perform(get("/api/messages/item/999999"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(put("/api/messages/"+messageId).header("Authorization","Bearer "+jwt(2,"STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"越权编辑\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/messages/"+messageId).header("Authorization","Bearer "+jwt(7,"STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"周末可以试听吗？\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").value("周末可以试听吗？"));
        mvc.perform(delete("/api/messages/"+messageId).header("Authorization","Bearer "+jwt(2,"STUDENT")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/messages/"+messageId).header("Authorization","Bearer "+jwt(7,"STUDENT")))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(messages.existsById(messageId)).isFalse();
        String second=mvc.perform(post("/api/messages").header("Authorization","Bearer "+jwt(7,"STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":"+product.getId()+",\"content\":\"管理员删除测试\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long adminMessageId=objectMapper.readTree(second).path("data").path("id").asLong();
        mvc.perform(get("/api/admin/items").header("Authorization","Bearer "+jwt(1,"ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(product.getId()));
        mvc.perform(get("/api/admin/messages").header("Authorization","Bearer "+jwt(1,"ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(adminMessageId));
        mvc.perform(delete("/api/admin/messages/"+adminMessageId).header("Authorization","Bearer "+jwt(1,"ADMIN")))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(messages.existsById(adminMessageId)).isFalse();
        mvc.perform(delete("/api/admin/messages/"+adminMessageId).header("Authorization","Bearer "+jwt(1,"ADMIN")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false));
    }

    private Item item(long seller,String title,BigDecimal price){Item value=new Item();value.setSellerId(seller);value.setTitle(title);value.setCategory("教材");value.setPrice(price);value.setRegion("学院路校区");return items.saveAndFlush(value);}
    private void project(long id,String username,String nickname,String status,int credit,long version){LocalDateTime now=LocalDateTime.of(2026,8,27,12,0);projectionUpdater.accept(new UserPublicProfileChanged("event-"+id+"-"+version,"test-correlation",id,version,username,nickname,"学院路校区",credit,now,status,"STUDENT",now,now));}
    private void await(CountDownLatch latch){try{latch.await();}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}}
    private String jwt(long user,String role){Instant now=Instant.now();JwtClaimsSet claims=JwtClaimsSet.builder().issuer("campus-gateway").subject(String.valueOf(user)).claim("role",role).claim("email","u"+user+"@example.com").claim("auth_version",1).issuedAt(now).expiresAt(now.plusSeconds(60)).build();JwtEncoder encoder=NimbusJwtEncoder.withSecretKey(new SecretKeySpec(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256")).build();return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();}
}
