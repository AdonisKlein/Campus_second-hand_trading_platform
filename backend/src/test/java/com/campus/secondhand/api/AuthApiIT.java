package com.campus.secondhand.api;

import com.campus.secondhand.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthApiIT extends AbstractApiIntegrationTest {
    @Test
    void loginCreatesSessionAndLogoutRevokesIt() throws Exception {
        User student = createUser("auth-student", "auth-student@example.com", "STUDENT");
        MockCookie session = login(student.getEmail());
        mvc.perform(get("/users/me").cookie(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(student.getId()));
        mvc.perform(post("/auth/logout").cookie(session).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/users/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void loginFailureUsesSameMessageForUnknownAndWrongPassword() throws Exception {
        createUser("auth-known", "auth-known@example.com", "STUDENT");
        for (String email : new String[]{"auth-known@example.com", "missing@example.com"}) {
            mvc.perform(post("/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"%s\",\"password\":\"wrong123\"}".formatted(email)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("邮箱或密码错误"));
        }
    }

    @Test
    void authVersionChangeInvalidatesExistingSession() throws Exception {
        User student = createUser("auth-revoke", "auth-revoke@example.com", "STUDENT");
        MockCookie session = login(student.getEmail());
        student.setAuthVersion(student.getAuthVersion() + 1);
        users.saveAndFlush(student);
        mvc.perform(get("/users/me").cookie(session)).andExpect(status().isForbidden());
    }
}
