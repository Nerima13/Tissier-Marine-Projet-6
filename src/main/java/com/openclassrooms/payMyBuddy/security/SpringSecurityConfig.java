package com.openclassrooms.payMyBuddy.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SpringSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Open public pages and static assets
                        .requestMatchers("/login", "/register", "/css/**", "/js/**", "/images/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form

                        // Use custom login page
                        // .loginPage("/login")
                        // Where to go after successful login
                        .defaultSuccessUrl("/transfer", true)
                        .permitAll())

                // OAuth2 will also use the same custom login page
                // .oauth2Login(o -> o.loginPage("/login"))
                .oauth2Login(Customizer.withDefaults())
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll());

        return http.build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
