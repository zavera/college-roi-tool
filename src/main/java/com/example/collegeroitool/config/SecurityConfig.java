package com.example.collegeroitool.config;

import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @Value("${premium.dev.bypass:false}")
    private boolean devBypass;

    public SecurityConfig(UserService userService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        // Dev bypass: no login required — the whole app is open
        if (devBypass) {
            http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"));
            return http.build();
        }

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/login.html", "/register.html",
                    "/api/auth/register",
                    "/api/stripe/webhook",
                    "/astra-logo.jpg",
                    "/callisto_high.png",
                    "/error"
                ).permitAll()
                .anyRequest().authenticated()
            )
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
            .oauth2Login(oauth -> oauth
                .loginPage("/login.html")
                .authorizationEndpoint(ep -> ep
                    .authorizationRequestRepository(new CookieOAuth2AuthorizationRequestRepository())
                )
                .successHandler(oAuth2SuccessHandler())
            )
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
            .exceptionHandling(ex -> ex.authenticationEntryPoint(authEntryPoint()))
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/api/stripe/webhook"));

        return http.build();
    }

    private AuthenticationEntryPoint authEntryPoint() {
        return (request, response, authException) -> {
            String xhr = request.getHeader("X-Requested-With");
            String accept = request.getHeader("Accept");
            boolean isAjax = "XMLHttpRequest".equals(xhr)
                || (accept != null && accept.contains("application/json"));
            if (isAjax) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"loggedIn\":false}");
            } else {
                response.sendRedirect("/login.html");
            }
        };
    }

    private AuthenticationSuccessHandler jsonSuccessHandler() {
        return (request, response, authentication) -> {
            String email = authentication.getName();
            AppUser user = userService.findByEmail(email).orElse(null);
            boolean subscribed = user != null && user.isSubscriptionActive();
            String name = user != null && user.getName() != null ? user.getName() : email;
            response.setContentType("application/json");
            response.getWriter().write(String.format(
                "{\"loggedIn\":true,\"email\":\"%s\",\"name\":\"%s\",\"subscriptionActive\":%b}",
                email, name, subscribed));
        };
    }

    private AuthenticationSuccessHandler oAuth2SuccessHandler() {
        return (request, response, authentication) -> {
            try {
                OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
                userService.findOrCreateGoogleUser(oAuth2User);
            } catch (Exception ignored) {
                // user-persist failure must not block the redirect
            }
            response.sendRedirect("/");
        };
    }
}
