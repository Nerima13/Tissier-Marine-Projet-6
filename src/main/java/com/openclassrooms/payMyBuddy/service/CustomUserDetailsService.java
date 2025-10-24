package com.openclassrooms.payMyBuddy.service;

import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    private String maskEmail(String email) {
        return (email == null) ? "unknown" : email.trim().toLowerCase().replaceAll("(^.).*(@.*$)", "$1***$2");
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        if (email == null) {
            log.warn("CustomUserDetailsService.loadUserByUsername - null email");
            throw new UsernameNotFoundException("User not found : null");
        }

        String normalizedEmail = email.trim().toLowerCase();
        log.info("CustomUserDetailsService.loadUserByUsername - attempt email={}", maskEmail(normalizedEmail));

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> {
                    log.warn("CustomUserDetailsService.loadUserByUsername - not found email={}", maskEmail(normalizedEmail));
                    return new UsernameNotFoundException("User not found : " + normalizedEmail);
                });

        // the 'Bank' account is not authorized to authenticate
        if (user.isBank()) {
            log.warn("CustomUserDetailsService.loadUserByUsername - bank user blocked email={}", maskEmail(user.getEmail()));
            throw new UsernameNotFoundException("User not found : " + normalizedEmail);
        }

        log.info("CustomUserDetailsService.loadUserByUsername - success userId={} email={}",
                user.getId(), maskEmail(user.getEmail()));

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
