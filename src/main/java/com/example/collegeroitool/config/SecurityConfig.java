package com.example.collegeroitool.config;

import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserService userService;

    public SecurityConfig(UserService userService) {
        this.userService = userService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Public: login, register, static assets
                .requestMatchers(
                    "/login.html", "/register.html",
                    "/api/auth/register",
                    "/astra-logo.jpg",
                    "/error"
                ).permitAll()
                // Everything else requires login
                .anyRequest().authenticated()
            )
            // Form login (email/password)
            .formLogin(form -> form
                .loginPage("/login.html")
                .loginProcessingUrl("/api/auth/login")
                .successHandler(jsonSuccessHandler())
                .failureHandler((req, res, ex) -> {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"Invalid email or password.\"}");
                })
                .permitAll()
            )
            // Google OAuth2
            .oauth2Login(oauth -> oauth
                .loginPage("/login.html")
                .successHandler(oAuth2SuccessHandler())
            )
            // Logout
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((req, res, auth) -> {
                    res.setStatus(HttpServletResponse.SC_OK);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"loggedOut\":true}");
                })
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            )
            // CSRF: disable for API calls from jQuery
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**")
            );

        return http.build();
    }

    /** After email/password login: return JSON so the SPA can react */
    private AuthenticationSuccessHandler jsonSuccessHandler() {
        return (request, response, authentication) -> {
            String email = authentication.getName();
            AppUser user = userService.findByEmail(email).orElse(null);
            boolean subscribed = user != null && user.isSubscriptionActive();
            String name = user != null ? user.getName() : email;
            response.setContentType("application/json");
            response.getWriter().write(String.format(
                "{\"loggedIn\":true,\"email\":\"%s\",\"name\":\"%s\",\"subscriptionActive\":%b}",
                email, name, subscribed));
        };
    }

    /** After Google OAuth2 login: find/create user then redirect to app */
    private AuthenticationSuccessHandler oAuth2SuccessHandler() {
        return (request, response, authentication) -> {
            OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
            userService.findOrCreateGoogleUser(oAuth2User);
            response.sendRedirect("/");
        };
    }
}
