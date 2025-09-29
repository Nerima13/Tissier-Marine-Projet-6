package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.dto.ProfileForm;
import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfileController.class)
public class ProfileControllerIT {

    @Autowired
    MockMvc mvc;

    @MockBean
    UserService userService;

    // 1) GET /profile: returns 200 + view "profile" + minimal model
    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void get_profile_ok() throws Exception {
        User me = new User();
        me.setId(1);
        me.setEmail("user@example.com");
        me.setUsername("John");

        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(me));

        mvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(view().name("profile"))
                .andExpect(model().attributeExists("me"))
                .andExpect(model().attributeExists("profileForm"));

        verify(userService).getUserByEmail("user@example.com");
    }

    // 2) POST /profile success (email unchanged): redirects + flash "success"
    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void post_profile_success_same_email() throws Exception {
        // Return a dummy User: controller does not use it
        when(userService.updateProfile(
                "user@example.com", "John", " user@example.com ", "curr", "newPassword", "newPassword"))
                .thenReturn(new User());

        mvc.perform(post("/profile")
                        .param("username", "John")
                        .param("email", " user@example.com ") // same email (with spaces)
                        .param("currentPassword", "curr")
                        .param("newPassword", "newPassword")
                        .param("confirmPassword", "newPassword")
                        .with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("success", "Profile updated."));

        verify(userService).updateProfile(
                "user@example.com", "John", " user@example.com ", "curr", "newPassword", "newPassword");
    }

    // 3) POST /profile error (service throws): redirects + flash "error" + keeps the form
    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void post_profile_error_keeps_form() throws Exception {
        doThrow(new IllegalArgumentException("Current password is incorrect."))
                .when(userService).updateProfile(
                        "user@example.com", "John", "user@example.com", "bad", "newPassword", "newPassword");

        mvc.perform(post("/profile")
                        .param("username", "John")
                        .param("email", "user@example.com")
                        .param("currentPassword", "bad")
                        .param("newPassword", "newPassword")
                        .param("confirmPassword", "newPassword")
                        .with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("error", "Current password is incorrect."))
                .andExpect(flash().attributeExists("profileForm"));

        verify(userService).updateProfile(
                "user@example.com", "John", "user@example.com", "bad", "newPassword", "newPassword");
    }
}