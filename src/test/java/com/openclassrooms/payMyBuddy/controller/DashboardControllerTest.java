package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.dto.FriendDTO;
import com.openclassrooms.payMyBuddy.dto.TransferDTO;
import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.service.CurrentUserService;
import com.openclassrooms.payMyBuddy.service.TransactionService;
import com.openclassrooms.payMyBuddy.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    UserService userService;

    @Mock
    TransactionService transactionService;

    @Mock
    CurrentUserService currentUserService;

    @InjectMocks
    DashboardController controller;

    // 1) GET /transfer with null auth -> IllegalStateException (thrown by requireEmail)
    @Test
    void showDashboard_whenAuthIsNull_throws() {
        Model model = new ConcurrentModel();

        when(currentUserService.requireEmail(null)).thenThrow(new IllegalStateException("Not authenticated"));

        assertThrows(IllegalStateException.class, () -> controller.showDashboard(null, model));

        verify(currentUserService).requireEmail(null);
        verifyNoInteractions(userService, transactionService);
    }

    // 2) GET /transfer with valid auth but user not found -> IllegalArgumentException
    @Test
    void showDashboard_whenUserNotFound_throws() {
        Model model = new ConcurrentModel();
        var auth = new TestingAuthenticationToken("user@example.com", "pwd");

        when(currentUserService.requireEmail(auth)).thenReturn("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> controller.showDashboard(auth, model));

        verify(currentUserService).requireEmail(auth);
        verify(userService).getUserByEmail("user@example.com");
        verifyNoMoreInteractions(userService);
        verifyNoInteractions(transactionService);
    }

    // 3) GET /transfer happy path -> returns "dashboard" and fills the model
    @Test
    void showDashboard_success_returnsDashboard_andFillsModel() {
        Model model = new ConcurrentModel();
        var auth = new TestingAuthenticationToken("user@example.com", "pwd");

        User me = mock(User.class);
        when(me.getId()).thenReturn(1);
        when(me.getConnections()).thenReturn(Set.of());

        when(currentUserService.requireEmail(auth)).thenReturn("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(me));
        when(transactionService.getFeedForUser(1)).thenReturn(List.of());

        String view = controller.showDashboard(auth, model);

        assertEquals("dashboard", view);
        assertSame(me, model.getAttribute("me"));
        assertNotNull(model.getAttribute("friends"));
        assertNotNull(model.getAttribute("feed"));
        assertTrue(model.getAttribute("friendForm") instanceof FriendDTO);
        assertTrue(model.getAttribute("transferForm") instanceof TransferDTO);

        verify(currentUserService).requireEmail(auth);
        verify(userService).getUserByEmail("user@example.com");
        verify(transactionService).getFeedForUser(1);
        verifyNoMoreInteractions(userService, transactionService);
    }

    // 4) POST /connections with null auth -> IllegalStateException
    @Test
    void addFriend_whenAuthIsNull_throws() {
        RedirectAttributes ra = new RedirectAttributesModelMap();
        FriendDTO form = new FriendDTO();

        when(currentUserService.requireEmail(null)).thenThrow(new IllegalStateException("Not authenticated"));

        assertThrows(IllegalStateException.class, () -> controller.addFriend(form, null, ra));

        verify(currentUserService).requireEmail(null);
        assertTrue(ra.getFlashAttributes().isEmpty());
        verifyNoInteractions(userService, transactionService);
    }

    // 5) POST /connections with blank email -> error + redirect /connections
    @Test
    void addFriend_whenEmailBlank_redirectsWithError() {
        RedirectAttributes ra = new RedirectAttributesModelMap();
        FriendDTO form = new FriendDTO();
        form.setEmail("   ");
        var auth = new TestingAuthenticationToken("user@example.com", "pwd");

        when(currentUserService.requireEmail(auth)).thenReturn("user@example.com");

        String view = controller.addFriend(form, auth, ra);

        assertEquals("redirect:/connections", view);
        assertEquals("Please enter a friend's email.", ra.getFlashAttributes().get("error"));
        verify(currentUserService).requireEmail(auth);
        verify(userService, never()).addConnection(anyString(), anyString());
        verifyNoInteractions(transactionService);
    }

    // 6) POST /connections trying to add yourself -> error + redirect /connections
    @Test
    void addFriend_whenAddingSelf_redirectsWithError() {
        RedirectAttributes ra = new RedirectAttributesModelMap();
        FriendDTO form = new FriendDTO();
        form.setEmail(" USER@EXAMPLE.COM ");
        var auth = new TestingAuthenticationToken("user@example.com", "pwd");

        when(currentUserService.requireEmail(auth)).thenReturn("user@example.com");

        String view = controller.addFriend(form, auth, ra);

        assertEquals("redirect:/connections", view);
        assertEquals("You cannot add yourself.", ra.getFlashAttributes().get("error"));
        verify(currentUserService).requireEmail(auth);
        verify(userService, never()).addConnection(anyString(), anyString());
        verifyNoInteractions(transactionService);
    }

    // 7) POST /connections success -> calls service + flash success
    @Test
    void addFriend_success_callsService_andRedirectsWithSuccess() {
        RedirectAttributes ra = new RedirectAttributesModelMap();
        FriendDTO form = new FriendDTO();
        form.setEmail("  FRIEND@Example.COM  ");
        var auth = new TestingAuthenticationToken("user@example.com", "pwd");

        when(currentUserService.requireEmail(auth)).thenReturn("user@example.com");

        String view = controller.addFriend(form, auth, ra);

        assertEquals("redirect:/connections", view);
        assertEquals("Friend added !", ra.getFlashAttributes().get("success")); // <-- espace !
        verify(currentUserService).requireEmail(auth);
        verify(userService).addConnection("user@example.com", "friend@example.com");
        verifyNoMoreInteractions(userService);
        verifyNoInteractions(transactionService);
    }

    // 8) POST /connections service jette -> flash erreur + redirect /connections
    @Test
    void addFriend_whenServiceThrows_redirectsWithError() {
        RedirectAttributes ra = new RedirectAttributesModelMap();
        FriendDTO form = new FriendDTO();
        form.setEmail("friend@example.com");
        var auth = new TestingAuthenticationToken("user@example.com", "pwd");

        when(currentUserService.requireEmail(auth)).thenReturn("user@example.com");
        doThrow(new IllegalArgumentException("User not found"))
                .when(userService).addConnection("user@example.com", "friend@example.com");

        String view = controller.addFriend(form, auth, ra);

        assertEquals("redirect:/connections", view);
        assertEquals("User not found", ra.getFlashAttributes().get("error"));
        verify(currentUserService).requireEmail(auth);
        verify(userService).addConnection("user@example.com", "friend@example.com");
        verifyNoMoreInteractions(userService);
        verifyNoInteractions(transactionService);
    }

    // 9) GET /connections -> "connections", 'me' in model, friendForm present, active="add"
    @Test
    void showAddFriend_success_fillsModel() {
        Model model = new ConcurrentModel();
        var auth = new TestingAuthenticationToken("user@example.com", "pwd");

        User me = mock(User.class);
        when(currentUserService.requireEmail(auth)).thenReturn("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(me));

        String view = controller.showAddFriend(auth, model);

        assertEquals("connections", view);
        assertSame(me, model.getAttribute("me"));
        assertTrue(model.getAttribute("friendForm") instanceof FriendDTO);
        assertEquals("add", model.getAttribute("active"));

        verify(currentUserService).requireEmail(auth);
        verify(userService).getUserByEmail("user@example.com");
        verifyNoMoreInteractions(userService);
        verifyNoInteractions(transactionService);
    }
}
