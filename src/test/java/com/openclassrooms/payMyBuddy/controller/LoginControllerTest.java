package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.config.TestUsersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUsersConfig.class)
public class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void shouldReturnDefaultMessage() throws Exception {
        mockMvc.perform(get("/login")).andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    public void userLoginTest() throws Exception {
        mockMvc.perform(formLogin("/login")
                        .user("user@test.com")
                        .password("user"))
                .andExpect(authenticated().withUsername("user@test.com"));
    }

    @Test
    public void userLoginFailed() throws Exception {
        mockMvc.perform(formLogin("/login")
                        .user("user")
                        .password("wrongpassword"))
                .andExpect(unauthenticated());
    }

    @Test
    @WithMockUser
    public void shouldReturnUserPage() throws Exception {
        mockMvc.perform(get("/user")).andDo(print())
                .andExpect(status().isOk());
    }

}
