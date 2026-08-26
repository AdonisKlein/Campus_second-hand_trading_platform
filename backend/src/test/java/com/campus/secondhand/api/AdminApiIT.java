package com.campus.secondhand.api;

import com.campus.secondhand.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminApiIT extends AbstractApiIntegrationTest {
    @Test
    void studentCannotImpersonateAdminOrListUsers() throws Exception {
        User admin = saveUser("admin-list", "admin-list@example.com", "ADMIN");
        User student = saveUser("student-list", "student-list@example.com", "STUDENT");
        var session = login(student.getEmail());
        mvc.perform(get("/admin/users").param("adminId", admin.getId().toString()).cookie(session))
            .andExpect(status().isForbidden());
    }

    @Test
    void disablingAccountInvalidatesExistingSessionAndRestoreKeepsItInvalid() throws Exception {
        User admin = saveUser("admin-status", "admin-status@example.com", "ADMIN");
        User student = saveUser("student-status", "student-status@example.com", "STUDENT");
        var adminSession = login(admin.getEmail());
        var studentSession = login(student.getEmail());
        int originalVersion = student.getAuthVersion();
        mvc.perform(put("/admin/users/{id}/status", student.getId()).cookie(adminSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}"))
            .andExpect(status().isOk());
        assertEquals("DISABLED", users.findById(student.getId()).orElseThrow().getStatus());
        mvc.perform(get("/users/me").cookie(studentSession)).andExpect(status().isForbidden());
        mvc.perform(put("/admin/users/{id}/status", student.getId()).cookie(adminSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
            .andExpect(status().isOk());
        User restored = users.findById(student.getId()).orElseThrow();
        assertEquals("ACTIVE", restored.getStatus());
        assertEquals(true, restored.getAuthVersion() > originalVersion);
        mvc.perform(get("/users/me").cookie(studentSession)).andExpect(status().isForbidden());
    }
}
