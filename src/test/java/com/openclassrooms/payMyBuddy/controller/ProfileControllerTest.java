package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.dto.ProfileForm;
import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    UserService userService;

    @InjectMocks
    ProfileController controller;

    // 1) GET /profile with existing user => returns "profile" and fills model (me + default form)
    @Test
    void getProfil_success_returnsProfile_andFillsModel() {
        Model model = new ConcurrentModel();
        Principal principal = () -> "user@example.com";

        User me = mock(User.class);
        when(me.getUsername()).thenReturn("John");
        when(me.getEmail()).thenReturn("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(me));

        String view = controller.getProfil(model, principal);

        assertEquals("profile", view);
        assertSame(me, model.getAttribute("me"));
        assertTrue(model.containsAttribute("profileForm"));
        ProfileForm form = (ProfileForm) model.getAttribute("profileForm");
        assertNotNull(form);
        assertEquals("John", form.getUsername());
        assertEquals("user@example.com", form.getEmail());
        // Also verify the active tab
        assertEquals("profile", model.getAttribute("active"));

        verify(userService).getUserByEmail("user@example.com");
        verifyNoMoreInteractions(userService);
    }

    // 2) GET /profile keeps the existing form if already present (after a redirect with errors)
    @Test
    void getProfil_whenFormAlreadyInModel_keepsExistingForm() {
        ConcurrentModel model = new ConcurrentModel();
        Principal principal = () -> "user@example.com";

        ProfileForm existing = new ProfileForm();
        existing.setUsername("ExistingName");
        existing.setEmail("existing@example.com");
        model.addAttribute("profileForm", existing);

        User me = mock(User.class);
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(me));

        String view = controller.getProfil(model, principal);

        assertEquals("profile", view);
        assertSame(existing, model.getAttribute("profileForm")); // unchanged
        assertSame(me, model.getAttribute("me"));
        //  Also verify the active tab
        assertEquals("profile", model.getAttribute("active"));

        verify(userService).getUserByEmail("user@example.com");
        verifyNoMoreInteractions(userService);
    }

    // 3) GET /profile when user not found => IllegalArgumentException
    @Test
    void getProfil_whenUserNotFound_throws() {
        Model model = new ConcurrentModel();
        Principal principal = () -> "missing@example.com";

        when(userService.getUserByEmail("missing@example.com")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.getProfil(model, principal));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertTrue(ex.getReason().contains("User not found: missing@example.com"));

        verify(userService).getUserByEmail("missing@example.com");
        verifyNoMoreInteractions(userService);
    }

    // POST /profile

    // 4) POST /profile success without email change => flashes "Profile updated."
    @Test
    void postProfil_success_noEmailChange_flashesSimpleSuccess() {
        RedirectAttributes ra = new RedirectAttributesModelMap();
        Principal principal = () -> "user@example.com";

        ProfileForm form = new ProfileForm();
        form.setUsername("John");
        form.setEmail(" user@example.com "); // same email after trim/ignoreCase
        form.setCurrentPassword("curr");
        form.setNewPassword("new");
        form.setConfirmPassword("new");

        String view = controller.postProfil(form, principal, ra);

        assertEquals("redirect:/profile", view);
        // verify service call
        verify(userService).updateProfile("user@example.com", "John", " user@example.com ", "curr", "new", "new");
        verifyNoMoreInteractions(userService);

        // check flash attributes
        assertEquals("Profile updated.", ra.getFlashAttributes().get("success"));
        assertFalse(ra.getFlashAttributes().containsKey("error"));
        assertFalse(ra.getFlashAttributes().containsKey("profileForm"));
    }

    // 5) POST /profile success with email change => flashes "Profile updated. If you changed your email, please sign in again."
    @Test
    void postProfil_success_emailChanged_flashesReLoginNotice() {
        RedirectAttributes ra = new RedirectAttributesModelMap();
        Principal principal = () -> "user@example.com";

        ProfileForm form = new ProfileForm();
        form.setUsername("John");
        form.setEmail(" new.email@example.com "); // different after trim/lowercase compare
        form.setCurrentPassword("curr");
        form.setNewPassword("new");
        form.setConfirmPassword("new");

        String view = controller.postProfil(form, principal, ra);

        assertEquals("redirect:/profile", view);
        verify(userService).updateProfile("user@example.com", "John", " new.email@example.com ", "curr", "new", "new");
        verifyNoMoreInteractions(userService);

        assertEquals("Profile updated. If you changed your email, please sign in again.",
                ra.getFlashAttributes().get("success"));
        assertFalse(ra.getFlashAttributes().containsKey("error"));
        assertFalse(ra.getFlashAttributes().containsKey("profileForm"));
    }

    // 6) POST /profile service throws => flashes error + redirect:/profile while keeping a "safe form"
    @Test
    void postProfil_whenServiceThrows_flashesError_andKeepsSafeForm() {
        RedirectAttributes ra = new RedirectAttributesModelMap();
        Principal principal = () -> "user@example.com";

        ProfileForm form = new ProfileForm();
        form.setUsername("John");
        form.setEmail("user@example.com");
        form.setCurrentPassword("bad");
        form.setNewPassword("new");
        form.setConfirmPassword("new");

        doThrow(new IllegalArgumentException("Wrong current password"))
                .when(userService).updateProfile("user@example.com", "John", "user@example.com", "bad", "new", "new");

        String view = controller.postProfil(form, principal, ra);

        assertEquals("redirect:/profile", view);
        verify(userService).updateProfile("user@example.com", "John", "user@example.com", "bad", "new", "new");
        verifyNoMoreInteractions(userService);

        assertEquals("Wrong current password", ra.getFlashAttributes().get("error"));

        // The controller puts a NEW "safe" ProfileForm into the flashes (not the same instance)
        assertTrue(ra.getFlashAttributes().containsKey("profileForm"));
        Object flashed = ra.getFlashAttributes().get("profileForm");
        assertNotSame(form, flashed);
        assertTrue(flashed instanceof ProfileForm);
        ProfileForm safe = (ProfileForm) flashed;

        // The "safe form" only contains username & email (password fields are not copied)
        assertEquals("John", safe.getUsername());
        assertEquals("user@example.com", safe.getEmail());
        assertNull(safe.getCurrentPassword());
        assertNull(safe.getNewPassword());
        assertNull(safe.getConfirmPassword());

        assertFalse(ra.getFlashAttributes().containsKey("success"));
    }
}
