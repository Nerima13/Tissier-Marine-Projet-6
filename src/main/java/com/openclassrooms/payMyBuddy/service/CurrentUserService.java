package com.openclassrooms.payMyBuddy.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CurrentUserService {

    // Return the user's email (form login or OAuth2), or throw if not found
    public String requireEmail(Authentication auth) {
        if (auth == null) {
            throw new IllegalStateException("Cannot find user email.");
        }

        // OAuth2: read "email" from user attributes
        if (auth instanceof OAuth2AuthenticationToken oauth) {
            Map<String, Object> attrs = oauth.getPrincipal().getAttributes();
            Object value = attrs.get("email");
            if (value instanceof String) {
                String email = ((String) value).trim().toLowerCase();
                if (!email.isEmpty()) return email;
            }
            throw new IllegalStateException("Cannot find user email.");
        }

        // Form login: username is the email
        String name = auth.getName();
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalStateException("Cannot find user email.");
        }
        return name.trim().toLowerCase();
    }
}
