package com.openclassrooms.payMyBuddy.controller;
 import com.openclassrooms.payMyBuddy.service.UserService;
 import org.springframework.security.core.Authentication;
 import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
 import org.springframework.stereotype.Controller;
 import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.Map;

@Controller
public class LoginController {

    private final UserService userService;

    public LoginController(UserService userService) {
        this.userService = userService;
    }

    // Login page
    @GetMapping("/login")
    public String login(Model model) {
        return "login"; // => templates/login.html
    }

    // Home page after login
    @GetMapping("/")
    public String home(Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }

        if (authentication instanceof OAuth2AuthenticationToken) {
            OAuth2AuthenticationToken authToken = (OAuth2AuthenticationToken) authentication;

            // Account information (Google, GitHub…)
            Map<String, Object> infos = authToken.getPrincipal().getAttributes();

            // Email (safe extraction)
            Object emailObj = infos.get("email");
            String email = (emailObj != null) ? emailObj.toString() : null;

            // Name (check several keys depending on the provider)
            String name = extractName(infos);

            if (email == null) {
                return "redirect:/login"; // cannot create a user without email
            }

            // Create user if necessary (business logic in the service)
            userService.getOrCreateOAuth2User(email, name);

            return "redirect:/transfer";
        }

        return "redirect:/login";
    }

    // Extract a display name depending on the provider
    private String extractName(Map<String, Object> infos) {
        Object n1 = infos.get("name");                // Google
        Object n2 = infos.get("login");               // GitHub
        if (n1 != null) return String.valueOf(n1);
        if (n2 != null) return String.valueOf(n2);
        return null;
    }
}
