package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfileController.class)
class ProfileControllerIT {

    @Autowired MockMvc mvc;

    @MockBean UserService userService;

    @Test
    void get_profile_ok() throws Exception {
        User me = new User();
        me.setEmail("user@example.com");
        me.setUsername("John");
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(me));

        mvc.perform(get("/profile").with(user("user@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(view().name("profile"))
                .andExpect(model().attributeExists("me", "profileForm"))
                .andExpect(model().attribute("active", "profile"));

        verify(userService).getUserByEmail("user@example.com");
    }

    @Test
    void post_profile_success_same_email() throws Exception {
        mvc.perform(post("/profile")
                        .with(user("user@example.com").roles("USER"))
                        .with(csrf())
                        .param("username", "John")
                        .param("email", " user@example.com ")
                        .param("currentPassword", "curr")
                        .param("newPassword", "new")
                        .param("confirmPassword", "new"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("success", "Profile updated."));

        verify(userService).updateProfile("user@example.com", "John", " user@example.com ", "curr", "new", "new");
    }

    @Test
    void post_profile_success_email_changed() throws Exception {
        mvc.perform(post("/profile")
                        .with(user("user@example.com").roles("USER"))
                        .with(csrf())
                        .param("username", "John")
                        .param("email", " new.email@example.com ")
                        .param("currentPassword", "curr")
                        .param("newPassword", "new")
                        .param("confirmPassword", "new"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("success",
                        "Profile updated. If you changed your email, please sign in again."));

        verify(userService).updateProfile(
                "user@example.com", "John", " new.email@example.com ", "curr", "new", "new");
    }

    @Test
    void post_profile_error_keeps_form() throws Exception {
        doThrow(new IllegalArgumentException("Current password is incorrect."))
                .when(userService).updateProfile(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());

        mvc.perform(post("/profile")
                        .with(user("user@example.com").roles("USER"))
                        .with(csrf())
                        .param("username", "John")
                        .param("email", "user@example.com")
                        .param("currentPassword", "bad")
                        .param("newPassword", "new")
                        .param("confirmPassword", "new"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("error", "Current password is incorrect."))
                .andExpect(flash().attributeExists("profileForm"));

        verify(userService).updateProfile(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void get_profile_user_not_found_returns_500() throws Exception {
        when(userService.getUserByEmail("missing@example.com")).thenReturn(Optional.empty());

        mvc.perform(get("/profile").with(user("missing@example.com").roles("USER")))
                .andExpect(status().isInternalServerError());
    }
}