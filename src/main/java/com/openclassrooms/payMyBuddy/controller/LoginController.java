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

        // OAuth2 (Google / GitHub / Facebook)
        if (authentication instanceof OAuth2AuthenticationToken token) {
            Map<String, Object> attrs = token.getPrincipal().getAttributes();

            // Required email (we need it to create or find the user)
            String email = null;
            if (attrs.get("email") != null) {
                String e = attrs.get("email").toString().trim().toLowerCase();
                if (!e.isEmpty()) {
                    email = e;
                }
            }
            if (email == null) { // without email we cannot create a user
                return "redirect:/login";
            }

            // Display name if available (optional)
            String name = null;
            if (attrs.get("name") != null) {
                name = attrs.get("name").toString().trim();       // Google / Facebook
            } else if (attrs.get("login") != null) {
                name = attrs.get("login").toString().trim();      // GitHub handle
            } else {
                String given = attrs.get("given_name") != null ? attrs.get("given_name").toString().trim() : null;
                String family = attrs.get("family_name") != null ? attrs.get("family_name").toString().trim() : null;

                // Facebook fallback for last name
                if ((family == null || family.isEmpty()) && attrs.get("last_name") != null) {
                    String last = attrs.get("last_name").toString().trim();
                    if (!last.isEmpty()) family = last;
                }
                if ((given != null && !given.isEmpty()) || (family != null && !family.isEmpty())) {
                    name = ((given != null ? given : "") + " " + (family != null ? family : "")).trim();
                }
            }
            if (name == null || name.isEmpty()) {
                name = email; // last simple and safe fallback
            }

            // Create or retrieve the user
            userService.getOrCreateOAuth2User(email, name);
        }

        return "redirect:/transfer";
    }
}
