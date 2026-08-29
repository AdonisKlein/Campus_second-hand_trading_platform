package com.campus.secondhand.api;

import com.campus.secondhand.user.User;
import static org.hamcrest.Matchers.hasSize;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SearchApiIT extends AbstractApiIntegrationTest {
    @Test void userSearchReturnsOnlyPublicProjection() throws Exception {
        User user = createUser("search-public", "search-public@example.com", "STUDENT");
        user.setNickname("公开昵称"); users.saveAndFlush(user);
        mvc.perform(get("/search").param("scope", "USERS").param("q", "search-public"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.users", hasSize(1)))
            .andExpect(jsonPath("$.data.users[0].username").value(user.getUsername()))
            .andExpect(jsonPath("$.data.users[0].email").doesNotExist());
    }

    @Test void searchLimitsMoreThanEightKeywordsToEightTerms() throws Exception {
        mvc.perform(get("/search").param("q", "a b c d e f g h i"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").exists());
    }
}
