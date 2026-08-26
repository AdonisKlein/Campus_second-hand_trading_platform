package com.campus.secondhand.api;

import com.campus.secondhand.item.SellerInventory;
import com.campus.secondhand.item.ItemModerationStatus;
import com.campus.secondhand.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GovernanceApiIT extends AbstractApiIntegrationTest {
    @Test
    void studentSubmitsOnceAndAdminRemovesItemWithDatabaseState() throws Exception {
        User seller = saveUser("govern-seller", "govern-seller@example.com", "STUDENT");
        User reporter = saveUser("govern-reporter", "govern-reporter@example.com", "STUDENT");
        User admin = saveUser("govern-admin", "govern-admin@example.com", "ADMIN");
        var item = sellerInventory.publish(seller.getId(), new SellerInventory.ItemDraft(
            "治理测试商品", "其他", java.math.BigDecimal.TEN, "需要核查", ""));
        MockCookie reporterSession = login(reporter.getEmail());
        MockCookie adminSession = login(admin.getEmail());

        String body = "{\"targetType\":\"ITEM\",\"targetId\":%d,\"reasonCode\":\"FRAUD\",\"description\":\"商品描述与实际情况明显不一致，请核查\"}"
            .formatted(item.id());
        mvc.perform(post("/reports").cookie(reporterSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reporterId").value(reporter.getId()))
            .andExpect(jsonPath("$.data.status").value("OPEN"));
        mvc.perform(post("/reports").cookie(reporterSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict());
        mvc.perform(get("/admin/reports").cookie(reporterSession))
            .andExpect(status().isForbidden());
        mvc.perform(get("/reports/mine").cookie(reporterSession))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.reports", hasSize(1)));
        Long reportId = contentReports.findAll().getFirst().getId();
        mvc.perform(put("/admin/reports/{id}", reportId).cookie(adminSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\",\"action\":\"REMOVE_ITEM\",\"note\":\"核查成立，商品已下架\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("RESOLVED"))
            .andExpect(jsonPath("$.data.history", hasSize(1)));
        assertEquals(ItemModerationStatus.REMOVED, items.findById(item.id()).orElseThrow().getModerationStatus());
        assertEquals(admin.getId(), reportActions.findAll().getFirst().getAdminId());
    }

    @Test
    void adminRejectsReportAndCannotUseMismatchedActionOrProcessTwice() throws Exception {
        User seller = saveUser("reject-seller", "reject-seller@example.com", "STUDENT");
        User reporter = saveUser("reject-reporter", "reject-reporter@example.com", "STUDENT");
        User admin = saveUser("reject-admin", "reject-admin@example.com", "ADMIN");
        var item = sellerInventory.publish(seller.getId(), new SellerInventory.ItemDraft(
            "无违规商品", "其他", java.math.BigDecimal.TEN, "普通描述", ""));
        MockCookie reporterSession = login(reporter.getEmail());
        MockCookie adminSession = login(admin.getEmail());
        mvc.perform(post("/reports").cookie(reporterSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"ITEM\",\"targetId\":%d,\"reasonCode\":\"OTHER\",\"description\":\"提交给管理员核查该商品描述\"}".formatted(item.id())))
            .andExpect(status().isOk());
        Long reportId = contentReports.findAll().getFirst().getId();
        mvc.perform(put("/admin/reports/{id}", reportId).cookie(adminSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DISMISSED\",\"action\":\"NONE\",\"note\":\"核查后未发现违规\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DISMISSED"));
        assertEquals(ItemModerationStatus.VISIBLE, items.findById(item.id()).orElseThrow().getModerationStatus());
        var mismatchItem = sellerInventory.publish(seller.getId(), new SellerInventory.ItemDraft(
            "措施不匹配商品", "其他", java.math.BigDecimal.ONE, "普通描述", ""));
        mvc.perform(post("/reports").cookie(reporterSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"ITEM\",\"targetId\":%d,\"reasonCode\":\"FRAUD\",\"description\":\"该商品需要管理员判断是否存在违规\"}".formatted(mismatchItem.id())))
            .andExpect(status().isOk());
        Long mismatchReportId = contentReports.findAll().stream()
            .filter(report -> report.getTargetId().equals(mismatchItem.id())).findFirst().orElseThrow().getId();
        mvc.perform(put("/admin/reports/{id}", mismatchReportId).cookie(adminSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\",\"action\":\"NONE\",\"note\":\"确认违规但未选择措施\"}"))
            .andExpect(status().isConflict());
        assertEquals(ItemModerationStatus.VISIBLE, items.findById(mismatchItem.id()).orElseThrow().getModerationStatus());
        mvc.perform(put("/admin/reports/{id}", reportId).cookie(adminSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\",\"action\":\"REMOVE_ITEM\",\"note\":\"重复处理\"}"))
            .andExpect(status().isConflict());
    }
}
