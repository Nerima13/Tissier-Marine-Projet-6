package com.openclassrooms.payMyBuddy.controller;

import com.openclassrooms.payMyBuddy.model.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@RestController
public class LoginController {

    private final OAuth2AuthorizedClientService authorizedClientService;

    public LoginController(OAuth2AuthorizedClientService authorizedClientService) {
        this.authorizedClientService = authorizedClientService;
    }

    @GetMapping("/user")
    public String getUser() {
        return "Welcome, User";
    }

    @GetMapping("/")
    public String getUserInfo(Principal user, @AuthenticationPrincipal OidcUser oidcUser) {
        StringBuffer userInfo = new StringBuffer();
        if (user instanceof UsernamePasswordAuthenticationToken) {
            userInfo.append(getUsernamePasswordLoginInfo(user));
        } else if(user instanceof OAuth2AuthenticationToken) {
            userInfo.append(getOAuth2LoginInfo(user, oidcUser));
        }
        return userInfo.toString();
    }

    private StringBuffer getUsernamePasswordLoginInfo(Principal user) {
        StringBuffer usernameInfo = new StringBuffer();
        UsernamePasswordAuthenticationToken token = (UsernamePasswordAuthenticationToken) user;
        if (token.isAuthenticated()) {
            User u = (User) token.getPrincipal();
            usernameInfo.append("Welcome, " + u.getUsername());
        } else {
            usernameInfo.append("NA");
        }
        return usernameInfo;
    }

    private StringBuffer getOAuth2LoginInfo(Principal user, OidcUser oidcUser) {
        StringBuffer protectedInfo = new StringBuffer();

        OAuth2AuthenticationToken authToken = (OAuth2AuthenticationToken) user;
        OAuth2AuthorizedClient client =
                authorizedClientService.loadAuthorizedClient(
                        authToken.getAuthorizedClientRegistrationId(),
                        authToken.getName());

        if (authToken.isAuthenticated() && client != null) {
            Map<String, Object> userAttributes =
                    ((OAuth2User) authToken.getPrincipal()).getAttributes();

            String accessToken = client.getAccessToken().getTokenValue();

            protectedInfo.append("Welcome, ").append(userAttributes.get("name")).append("<br><br>");
            protectedInfo.append("e-mail: ").append(userAttributes.get("email")).append("<br><br>");
            protectedInfo.append("Access token: ").append(accessToken);

            OidcIdToken idToken = oidcUser.getIdToken();
            if (idToken != null) {
                protectedInfo.append("idToken value : " + idToken.getTokenValue() + "<br>");
                protectedInfo.append("Token mapped values <br>");
                Map<String, Object> claims = idToken.getClaims();
                for(String key : claims.keySet()) {
                    protectedInfo.append(" " + key + " : " + claims.get(key) + "<br>");
                }
            }
        } else {
            protectedInfo.append("NA");
        }
        return protectedInfo;
    }
}
