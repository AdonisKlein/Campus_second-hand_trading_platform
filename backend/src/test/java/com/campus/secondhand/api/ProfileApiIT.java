package com.campus.secondhand.api;

import com.campus.secondhand.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProfileApiIT extends AbstractApiIntegrationTest {
    @Test
    void profileReadAndUpdatePersistOnlyAllowedFields() throws Exception {
        User student = createUser("profile-student", "profile-student@example.com", "STUDENT");
        MockCookie session = login(student.getEmail());
        mvc.perform(put("/users/me").cookie(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"新昵称\",\"phone\":\"13800000000\",\"campusRegion\":\"沙河校区\",\"email\":\"forged@example.com\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.nickname").value("新昵称"))
            .andExpect(jsonPath("$.data.email").value(student.getEmail()));
        User persisted = users.findById(student.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("新昵称", persisted.getNickname());
        org.junit.jupiter.api.Assertions.assertEquals("沙河校区", persisted.getCampusRegion());
        org.junit.jupiter.api.Assertions.assertEquals(student.getEmail(), persisted.getEmail());
    }

    @Test
    void profileRejectsInvalidRegionAndUnauthenticatedWrites() throws Exception {
        User student = createUser("profile-invalid", "profile-invalid@example.com", "STUDENT");
        MockCookie session = login(student.getEmail());
        mvc.perform(put("/users/me").cookie(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"x\",\"campusRegion\":\"校外地址\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/users/me").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"越权\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void profileViewDoesNotExposePasswordHash() throws Exception {
        User student = createUser("profile-safe", "profile-safe@example.com", "STUDENT");
        mvc.perform(get("/users/me").cookie(login(student.getEmail())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }
}
