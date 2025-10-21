package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.dto.RegisterForm;
import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.service.TransactionService;
import com.openclassrooms.payMyBuddy.service.UserService;
import com.openclassrooms.payMyBuddy.security.SpringSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc
@Import(SpringSecurityConfig.class)
public class AuthControllerIT {

    @Autowired MockMvc mvc;

    @MockBean UserService userService;
    @MockBean TransactionService transactionService;

    // 1) GET /register: returns 200 and view "register" with an empty form in the model
    @Test
    void get_register_renders_view_with_empty_form() throws Exception {
        mvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attribute("form", instanceOf(RegisterForm.class)));
    }

    // 2) POST /register with missing required fields: returns "register" with error and keeps the form
    @Test
    void post_register_missing_required_fields_returns_error() throws Exception {
        // No params at all => email/password/confirmPassword are null
        mvc.perform(post("/register").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attribute("error", "Please fill in all required fields."))
                .andExpect(model().attributeExists("form"));
    }

    // 3) POST /register with blank fields (after trim): returns "register" with error and keeps the form
    @Test
    void post_register_blank_fields_returns_error() throws Exception {
        mvc.perform(post("/register")
                        .param("email", "   ")
                        .param("password", "   ")
                        .param("confirmPassword", "   ")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attribute("error", "Please fill in all required fields."))
                .andExpect(model().attributeExists("form"));
    }

    // 4) POST /register with non-matching passwords: returns "register" with error and keeps the form
    @Test
    void post_register_passwords_do_not_match_returns_error() throws Exception {
        mvc.perform(post("/register")
                        .param("email", "user@example.com")
                        .param("password", "password1")
                        .param("confirmPassword", "password2")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attribute("error", "Passwords do not match."))
                .andExpect(model().attributeExists("form"));
    }

    // 5) POST /register success: delegates to service (with normalized values) and redirects to /login
    @Test
    void post_register_success_calls_service_and_redirects_to_login() throws Exception {
        mvc.perform(post("/register")
                        .param("email", "  JOHN.DOE@Example.COM  ") // will be trimmed+lowercased by the controller
                        .param("password", "  password  ")          // will be trimmed
                        .param("confirmPassword", "password")       // matches after trim
                        .param("username", "  John  ")              // optional, trimmed
                        .with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));

        // Verify the User object sent to the service is normalized
        verify(userService).registerUser(argThat((User u) ->
                u != null
                        && "john.doe@example.com".equals(u.getEmail())
                        && "password".equals(u.getPassword())
                        && "John".equals(u.getUsername())));
    }

    // 6) POST /register when service throws (email already used): returns "register" with service message and keeps the form
    @Test
    void post_register_service_throws_returns_error_and_keeps_form() throws Exception {
        doThrow(new IllegalArgumentException("Email already used"))
                .when(userService).registerUser(any(User.class));

        mvc.perform(post("/register")
                        .param("email", "taken@example.com")
                        .param("password", "secret")
                        .param("confirmPassword", "secret")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attribute("error", "Email already used"))
                .andExpect(model().attributeExists("form"));
    }
}