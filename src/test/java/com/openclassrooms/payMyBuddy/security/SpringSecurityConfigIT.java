package com.openclassrooms.payMyBuddy.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(com.openclassrooms.payMyBuddy.config.TestUsersConfig.class)
class SpringSecurityConfigIT {

    @Autowired MockMvc mvc;

    @Autowired BCryptPasswordEncoder passwordEncoder;

    // Public routes

    @Test
    void public_pages_are_accessible_without_auth() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/register")).andExpect(status().isOk());
    }

    @Test
    void public_static_assets_are_permitted() throws Exception {
        // No redirect to /login, even if the file does not exist
        mvc.perform(get("/css/app.css")).andExpect(status().isNotFound());
        mvc.perform(get("/js/app.js")).andExpect(status().isNotFound());
        mvc.perform(get("/images/logo.png")).andExpect(status().isNotFound());
    }

    // Protected routes

    @Test
    void protected_pages_require_authentication_redirect_to_login() throws Exception {
        mvc.perform(get("/transfer"))
                .andExpect(status().isFound()) // 302
                .andExpect(redirectedUrlPattern("**/login"));
    }

    // Login / Logout

    @Test
    void login_success_redirects_to_transfer() throws Exception {
        mvc.perform(post("/login")
                        .param("username", "user@test.com")
                        .param("password", "user")
                        .with(csrf()))
                .andExpect(status().isFound()) // 302 exact
                .andExpect(redirectedUrl("/transfer"));
    }

    @Test
    void login_failure_redirects_to_login_error() throws Exception {
        mvc.perform(post("/login")
                        .param("username", "user@test.com")
                        .param("password", "wrong")
                        .with(csrf()))
                .andExpect(status().isFound()) // 302 exact
                .andExpect(redirectedUrlPattern("/login?error"));
    }

    @Test
    void logout_without_csrf_is_forbidden() throws Exception {
        mvc.perform(post("/logout"))
                .andExpect(status().isForbidden()); // 403 exact
    }

    @Test
    void logout_with_csrf_redirects_to_login_logout() throws Exception {
        mvc.perform(post("/logout").with(csrf()))
                .andExpect(status().isFound()) // 302 exact
                .andExpect(redirectedUrl("/login?logout"));
    }

    // Password encoder bean

    @Test
    void passwordEncoder_isBCrypt_and_matches() {
        String raw = "password";
        String hash = passwordEncoder.encode(raw);

        assertTrue(passwordEncoder.matches(raw, hash));
    }
}