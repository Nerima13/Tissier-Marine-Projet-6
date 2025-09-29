package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.dto.ProfileForm;
import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProfileControllerTest {

    @Mock
    UserService userService;

    @InjectMocks
    ProfileController controller;

    // GET /profile

    // 1) GET /profile with existing user => returns "profile" and fills model (me + default form)
    @Test
    public void getProfil_success_returnsProfile_andFillsModel() {
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

        verify(userService).getUserByEmail("user@example.com");
        verifyNoMoreInteractions(userService);
    }

    // 2) GET /profile keeps existing form if already present (after redirect with errors)
    @Test
    public void getProfil_whenFormAlreadyInModel_keepsExistingForm() {
        ConcurrentModel model = new ConcurrentModel();
        Principal principal = () -> "user@example.com";

        // Existing form in the model (simulate flash after redirect)
        ProfileForm existing = new ProfileForm();
        existing.setUsername("ExistingName");
        existing.setEmail("existing@example.com");
        model.addAttribute("profileForm", existing);

        User me = mock(User.class); // no stubs needed
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(me));

        String view = controller.getProfil(model, principal);

        assertEquals("profile", view);
        assertSame(existing, model.getAttribute("profileForm")); // unchanged
        assertSame(me, model.getAttribute("me"));

        verify(userService).getUserByEmail("user@example.com");
        verifyNoMoreInteractions(userService);
    }

    // 3) GET /profile when user not found => throws IllegalArgumentException
    @Test
    public void getProfil_whenUserNotFound_throws() {
        Model model = new ConcurrentModel();
        Principal principal = () -> "missing@example.com";

        when(userService.getUserByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> controller.getProfil(model, principal));
        verify(userService).getUserByEmail("missing@example.com");
        verifyNoMoreInteractions(userService);
    }

    // POST /profile

    // 4) POST /profile success without email change => flashes "Profile updated."
    @Test
    public void postProfil_success_noEmailChange_flashesSimpleSuccess() {
        RedirectAttributes ra = new RedirectAttributesModelMap();
        Principal principal = () -> "user@example.com";

        ProfileForm form = new ProfileForm();
        form.setUsername("John");
        form.setEmail(" user@example.com "); // same after trim/ignore case
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
    public void postProfil_success_emailChanged_flashesReLoginNotice() {
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

    // 6) POST /profile when service throws => flashes error and returns redirect:/profile preserving the form
    @Test
    public void postProfil_whenServiceThrows_flashesError_andKeepsForm() {
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
        assertSame(form, ra.getFlashAttributes().get("profileForm")); // form is kept
        assertFalse(ra.getFlashAttributes().containsKey("success"));
    }
}