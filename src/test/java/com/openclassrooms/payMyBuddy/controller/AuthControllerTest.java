package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.dto.RegisterDTO;
import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthControllerTest {

    @Mock
    UserService userService;

    @InjectMocks
    AuthController controller;

    // 1) GET /register => returns "register" and adds an empty RegisterForm
    @Test
    public void showRegisterForm_returnsRegisterView_andAddsForm() {
        Model model = new ConcurrentModel();

        String view = controller.showRegisterForm(model);

        assertEquals("register", view);
        assertTrue(model.containsAttribute("form"));
        assertNotNull(model.getAttribute("form"));
        assertTrue(model.getAttribute("form") instanceof RegisterDTO);
        verifyNoInteractions(userService);
    }

    // 2) POST /register with null required fields => returns "register" with error
    @Test
    public void register_whenRequiredFieldsAreNull_returnsRegisterWithError() {
        Model model = new ConcurrentModel();
        RegisterDTO form = new RegisterDTO();
        form.setEmail(null);
        form.setPassword(null);
        form.setConfirmPassword(null);

        String view = controller.register(form, model);

        assertEquals("register", view);
        assertEquals("Please fill in all required fields.", model.getAttribute("error"));
        assertSame(form, model.getAttribute("form"));
        verifyNoInteractions(userService);
    }

    // 3) POST /register with empty (after trim) fields => returns "register" with error
    @Test
    public void register_whenFieldsAreBlankAfterTrim_returnsRegisterWithError() {
        Model model = new ConcurrentModel();
        RegisterDTO form = new RegisterDTO();
        form.setEmail("   ");
        form.setPassword("   ");
        form.setConfirmPassword("   ");

        String view = controller.register(form, model);

        assertEquals("register", view);
        assertEquals("Please fill in all required fields.", model.getAttribute("error"));
        assertSame(form, model.getAttribute("form"));
        verifyNoInteractions(userService);
    }

    // 4) POST /register with non-matching passwords => returns "register" with error
    @Test
    public void register_whenPasswordsDoNotMatch_returnsRegisterWithError() {
        Model model = new ConcurrentModel();
        RegisterDTO form = new RegisterDTO();
        form.setEmail("user@example.com");
        form.setPassword("password1");
        form.setConfirmPassword("password2");

        String view = controller.register(form, model);

        assertEquals("register", view);
        assertEquals("Passwords do not match.", model.getAttribute("error"));
        assertSame(form, model.getAttribute("form"));
        verifyNoInteractions(userService);
    }

    // 5) POST /register successful path => delegates to service and redirects to /login
    @Test
    public void register_success_callsService_withNormalizedValues_andRedirectsToLogin() {
        Model model = new ConcurrentModel();
        RegisterDTO form = new RegisterDTO();
        form.setEmail("  JOHN.DOE@Example.COM  ");   // will be trimmed + lowercased
        form.setPassword("  password  ");            // will be trimmed
        form.setConfirmPassword("password");         // matches after trim
        form.setUsername("  John  ");                // optional, will be trimmed

        String view = controller.register(form, model);

        assertEquals("redirect:/login", view);

        assertNull(model.getAttribute("error"));

        // Verify the User passed to the service
        verify(userService).registerUser(argThat(u ->
                u != null
                        && "john.doe@example.com".equals(u.getEmail())
                        && "password".equals(u.getPassword())
                        && "John".equals(u.getUsername())));
        verifyNoMoreInteractions(userService);
    }

    // 6) POST /register when service throws email already used => returns "register" with error message
    @Test
    public void register_whenServiceThrows_returnsRegisterWithServiceMessage() {
        Model model = new ConcurrentModel();
        RegisterDTO form = new RegisterDTO();
        form.setEmail("taken@example.com");
        form.setPassword("secret");
        form.setConfirmPassword("secret");

        doThrow(new IllegalArgumentException("Email already used"))
                .when(userService).registerUser(any(User.class));

        String view = controller.register(form, model);

        assertEquals("register", view);
        assertEquals("Email already used", model.getAttribute("error"));
        assertSame(form, model.getAttribute("form"));
        verify(userService).registerUser(any(User.class));
        verifyNoMoreInteractions(userService);
    }
}
