package com.example.collegeroitool.config;

import com.example.collegeroitool.model.AppUser;
import com.example.collegeroitool.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

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
            // Ensure auth is always persisted to the HTTP session (Spring Security 6 default
            // changed to requireExplicitSave=true which can silently drop the session after
            // OAuth2 login when custom success handlers are in use).
            .securityContext(ctx -> ctx
                .securityContextRepository(new HttpSessionSecurityContextRepository())
                .requireExplicitSave(false)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/", "/index.html", "/landing.html", "/app.html",
                    "/login.html", "/register.html", "/privacy-policy.html",
                    "/api/auth/register",
                    "/api/stripe/webhook",
                    "/astra-logo.jpg",
                    "/callisto_high.png",
                    "/sitemap.xml",
                    "/robots.txt",
                    "/BingSiteAuth.xml",
                    "/error",
                    "/api/demo/document/**"
                ).permitAll()
                // FAFSA Prep endpoints are restricted to institution users only
                // (users whose only membership is the default "callisto-tech" instance are blocked)
                .requestMatchers("/api/fafsa/**").access((authentication, context) -> {
                    String principal = authentication.get().getName();
                    boolean allowed = userService.hasInstitutionAccess(principal);
                    return new org.springframework.security.authorization.AuthorizationDecision(allowed);
                })
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
                .failureHandler(oAuth2FailureHandler())
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

    private AuthenticationFailureHandler oAuth2FailureHandler() {
        return (request, response, ex) -> {
            log.error("[OAuth2] FAILURE — {}: {}", ex.getClass().getSimpleName(), ex.getMessage());
            response.sendRedirect("/login.html?error=google");
        };
    }

    private AuthenticationSuccessHandler oAuth2SuccessHandler() {
        return (request, response, authentication) -> {
            OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
            String email = oAuth2User.getAttribute("email");
            log.info("[OAuth2] SUCCESS — email={} sessionId={}",
                    email, request.getSession(false) != null ? request.getSession(false).getId() : "NO SESSION");
            try {
                userService.findOrCreateGoogleUser(oAuth2User);
                log.info("[OAuth2] user persisted email={}", email);
            } catch (Exception e) {
                log.error("[OAuth2] user persist FAILED email={} error={}", email, e.getMessage(), e);
            }
            log.info("[OAuth2] redirecting to / for email={}", email);
            response.sendRedirect("/app.html");
        };
    }
}
