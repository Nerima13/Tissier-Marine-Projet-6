package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.dto.FriendForm;
import com.openclassrooms.payMyBuddy.dto.TransferForm;
import com.openclassrooms.payMyBuddy.model.User;
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

import java.math.BigDecimal;
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

    @InjectMocks
    DashboardController controller;

    // showDashboard

    // 1) GET /transfer with null auth => redirect to /login
    @Test
    public void showDashboard_whenAuthIsNull_redirectsToLogin() {
        Model model = new ConcurrentModel();

        String view = controller.showDashboard(null, model);

        assertEquals("redirect:/login", view);
        verifyNoInteractions(userService, transactionService);
    }

    // 2) GET /transfer with valid auth but user not found => redirect to /login
    @Test
    public void showDashboard_whenUserNotFound_redirectsToLogin() {
        Model model = new ConcurrentModel();
        var auth = new TestingAuthenticationToken("user@example.com", "pwd");

        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.empty());

        String view = controller.showDashboard(auth, model);

        assertEquals("redirect:/login", view);
        verify(userService).getUserByEmail("user@example.com");
        verifyNoMoreInteractions(userService);
        verifyNoInteractions(transactionService);
    }

    // 3) GET /transfer happy path => returns "dashboard" and fills model
    @Test
    public void showDashboard_success_returnsDashboard_andFillsModel() {
        Model model = new ConcurrentModel();
        TestingAuthenticationToken auth = new TestingAuthenticationToken("user@example.com", "pwd");

        User me = mock(User.class);
        when(me.getId()).thenReturn(1);
        when(me.getConnections()).thenReturn(Set.of());

        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(me));
        when(transactionService.getFeedForUser(1)).thenReturn(List.of());

        String view = controller.showDashboard(auth, model);

        assertEquals("dashboard", view);
        assertSame(me, model.getAttribute("me"));
        assertNotNull(model.getAttribute("friends"));
        assertNotNull(model.getAttribute("feed"));
        assertNotNull(model.getAttribute("friendForm"));
        assertNotNull(model.getAttribute("transferForm"));

        verify(userService).getUserByEmail("user@example.com");
        verify(transactionService).getFeedForUser(1);
        verifyNoMoreInteractions(userService, transactionService);
    }

    // addFriend

    // 4) POST /connections with null auth => redirect to /login
    @Test
    public void addFriend_whenAuthIsNull_redirectsToLogin() {
        Model model = new ConcurrentModel();
        FriendForm form = new FriendForm();

        String view = controller.addFriend(form, null, model);

        assertEquals("redirect:/login", view);
        verifyNoInteractions(userService, transactionService);
    }

    // 5) POST /connections with blank email => error and returns dashboard
    @Test
    public void addFriend_whenEmailBlank_returnsDashboardWithError() {
        Model model = new ConcurrentModel();
        FriendForm form = new FriendForm();
        form.setEmail("   "); // blank after trim
        TestingAuthenticationToken auth = new TestingAuthenticationToken("user@example.com", "pwd");

        User me = mock(User.class);
        when(me.getId()).thenReturn(1);
        when(me.getConnections()).thenReturn(Set.of());
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(me));
        when(transactionService.getFeedForUser(1)).thenReturn(List.of());

        String view = controller.addFriend(form, auth, model);

        assertEquals("dashboard", view);
        assertEquals("Please enter a friend's email.", model.getAttribute("error"));
        verify(userService).getUserByEmail("user@example.com");
        verify(transactionService).getFeedForUser(1);
        verify(userService, never()).addConnection(anyString(), anyString());
        verifyNoMoreInteractions(userService, transactionService);
    }

    // 6) POST /connections trying to add self => error and returns dashboard
    @Test
    public void addFriend_whenAddingSelf_returnsDashboardWithError() {
        Model model = new ConcurrentModel();
        FriendForm form = new FriendForm();
        form.setEmail(" USER@EXAMPLE.COM "); // same as current user, different case/spacing
        TestingAuthenticationToken auth = new TestingAuthenticationToken("user@example.com", "pwd");

        User me = mock(User.class);
        when(me.getId()).thenReturn(1);
        when(me.getConnections()).thenReturn(Set.of());
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(me));
        when(transactionService.getFeedForUser(1)).thenReturn(List.of());

        String view = controller.addFriend(form, auth, model);

        assertEquals("dashboard", view);
        assertEquals("You cannot add yourself.", model.getAttribute("error"));
        verify(userService).getUserByEmail("user@example.com");
        verify(transactionService).getFeedForUser(1);
        verify(userService, never()).addConnection(anyString(), anyString());
        verifyNoMoreInteractions(userService, transactionService);
    }

    // 7) POST /connections success => calls service with normalized email and returns dashboard with success
    @Test
    public void addFriend_success_callsService_andShowsSuccess() {
        Model model = new ConcurrentModel();
        FriendForm form = new FriendForm();
        form.setEmail("  FRIEND@Example.COM  "); // will be trimmed + lowercased
        TestingAuthenticationToken auth = new TestingAuthenticationToken("user@example.com", "pwd");

        User me = mock(User.class);
        when(me.getId()).thenReturn(1);
        when(me.getConnections()).thenReturn(Set.of());
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(me));
        when(transactionService.getFeedForUser(1)).thenReturn(List.of());

        String view = controller.addFriend(form, auth, model);

        assertEquals("dashboard", view);
        assertEquals("Friend added!", model.getAttribute("success"));
        verify(userService).addConnection("user@example.com", "friend@example.com");
        verify(userService).getUserByEmail("user@example.com");
        verify(transactionService).getFeedForUser(1);
        verifyNoMoreInteractions(userService, transactionService);
    }

    // 8) POST /connections when service throws => error message and returns dashboard
    @Test
    public void addFriend_whenServiceThrows_returnsDashboardWithError() {
        Model model = new ConcurrentModel();
        FriendForm form = new FriendForm();
        form.setEmail("friend@example.com");
        TestingAuthenticationToken auth = new TestingAuthenticationToken("user@example.com", "pwd");

        User me = mock(User.class);
        when(me.getId()).thenReturn(1);
        when(me.getConnections()).thenReturn(Set.of());
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(me));
        when(transactionService.getFeedForUser(1)).thenReturn(List.of());
        doThrow(new IllegalArgumentException("User not found"))
                .when(userService).addConnection("user@example.com", "friend@example.com");

        String view = controller.addFriend(form, auth, model);

        assertEquals("dashboard", view);
        assertEquals("User not found", model.getAttribute("error"));
        verify(userService).addConnection("user@example.com", "friend@example.com");
        verify(userService).getUserByEmail("user@example.com");
        verify(transactionService).getFeedForUser(1);
        verifyNoMoreInteractions(userService, transactionService);
    }

    // doTransfer

    // 9) POST /transfer with null auth => redirect to /login
    @Test
    public void doTransfer_whenAuthIsNull_redirectsToLogin() {
        Model model = new ConcurrentModel();
        TransferForm form = new TransferForm();

        String view = controller.doTransfer(form, null, model);

        assertEquals("redirect:/login", view);
        verifyNoInteractions(userService, transactionService);
    }

    // 10) POST /transfer with missing fields => error and returns dashboard
    @Test
    public void doTransfer_whenFieldsMissing_returnsDashboardWithError() {
        Model model = new ConcurrentModel();
        TransferForm form = new TransferForm(); // receiverEmail and amount are null
        TestingAuthenticationToken auth = new TestingAuthenticationToken("user@example.com", "pwd");

        User me = mock(User.class);
        when(me.getId()).thenReturn(1);
        when(me.getConnections()).thenReturn(Set.of());
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(me));
        when(transactionService.getFeedForUser(1)).thenReturn(List.of());

        String view = controller.doTransfer(form, auth, model);

        assertEquals("dashboard", view);
        assertEquals("Please select a receiver and enter an amount.", model.getAttribute("error"));
        verify(userService, times(2)).getUserByEmail("user@example.com");
        verify(transactionService).getFeedForUser(1);
        verify(userService, never()).transfer(anyInt(), anyString(), any(), any());
        verifyNoMoreInteractions(userService, transactionService);
    }

    // 11) POST /transfer success => calls service and shows success
    @Test
    public void doTransfer_success_callsService_andShowsSuccess() {
        Model model = new ConcurrentModel();
        TransferForm form = new TransferForm();
        form.setReceiverEmail("  FRIEND@Example.COM  "); // will be trimmed + lowercased
        form.setAmount(new BigDecimal("25.50"));
        form.setDescription("  Thanks  ");
        TestingAuthenticationToken auth = new TestingAuthenticationToken("user@example.com", "pwd");

        User me = mock(User.class);
        when(me.getId()).thenReturn(2);
        when(me.getConnections()).thenReturn(Set.of());
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(me));
        when(transactionService.getFeedForUser(2)).thenReturn(List.of());

        String view = controller.doTransfer(form, auth, model);

        assertEquals("dashboard", view);
        assertEquals("Transfer completed.", model.getAttribute("success"));
        verify(userService).transfer(2, "friend@example.com", new BigDecimal("25.50"), "  Thanks  ");
        verify(userService, times(2)).getUserByEmail("user@example.com");
        verify(transactionService).getFeedForUser(2);
        verifyNoMoreInteractions(userService, transactionService);
    }

    // 12) POST /transfer when service throws => error and returns dashboard
    @Test
    public void doTransfer_whenServiceThrows_returnsDashboardWithError() {
        Model model = new ConcurrentModel();
        TransferForm form = new TransferForm();
        form.setReceiverEmail("friend@example.com");
        form.setAmount(new BigDecimal("10.00"));
        form.setDescription("Coffee");
        TestingAuthenticationToken auth = new TestingAuthenticationToken("user@example.com", "pwd");

        User me = mock(User.class);
        when(me.getId()).thenReturn(7);
        when(me.getConnections()).thenReturn(Set.of());
        when(userService.getUserByEmail("user@example.com")).thenReturn(Optional.of(me));
        when(transactionService.getFeedForUser(7)).thenReturn(List.of());

        doThrow(new IllegalArgumentException("Insufficient balance"))
                .when(userService).transfer(7, "friend@example.com", new BigDecimal("10.00"), "Coffee");

        String view = controller.doTransfer(form, auth, model);

        assertEquals("dashboard", view);
        assertEquals("Insufficient balance", model.getAttribute("error"));
        verify(userService).transfer(7, "friend@example.com", new BigDecimal("10.00"), "Coffee");
        verify(userService, times(2)).getUserByEmail("user@example.com");
        verify(transactionService).getFeedForUser(7);
        verifyNoMoreInteractions(userService, transactionService);
    }
}
