package com.example.collegeroitool.auth;

import com.example.collegeroitool.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the full Spring Security auth filter chain.
 * Uses H2 in-memory — no external services needed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;

    private static final String EMAIL    = "alice@example.com";
    private static final String NAME     = "Alice";
    private static final String PASSWORD = "securePass1";

    @BeforeEach
    void resetDb() {
        userRepository.deleteAll();
    }

    // ── Registration ──────────────────────────────────────────────────────────

    @Test
    void register_newUser_succeeds() throws Exception {
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(NAME, EMAIL, PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.registered", is(true)))
            .andExpect(jsonPath("$.email", is(EMAIL)));
    }

    @Test
    void register_duplicateEmail_returns400() throws Exception {
        registerAlice();
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(NAME, EMAIL, PASSWORD)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").isString());
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(NAME, EMAIL, "short")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").isString());
    }

    @Test
    void register_emailCaseInsensitive_treatedAsDuplicate() throws Exception {
        registerAlice();
        // Register with uppercase variant of the same email
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(NAME, EMAIL.toUpperCase(), PASSWORD)))
            .andExpect(status().isBadRequest());
    }

    // ── Form login ────────────────────────────────────────────────────────────

    @Test
    void login_validCredentials_returnsLoggedInTrue() throws Exception {
        registerAlice();
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", EMAIL)
                .param("password", PASSWORD))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.loggedIn", is(true)))
            .andExpect(jsonPath("$.email", is(EMAIL)));
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        registerAlice();
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", EMAIL)
                .param("password", "wrongPassword"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").isString());
    }

    @Test
    void login_unknownUser_returns401() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", "nobody@example.com")
                .param("password", PASSWORD))
            .andExpect(status().isUnauthorized());
    }

    // ── /api/auth/me ──────────────────────────────────────────────────────────

    @Test
    void me_unauthenticated_returns401WithLoggedInFalse() throws Exception {
        mvc.perform(get("/api/auth/me")
                .header("X-Requested-With", "XMLHttpRequest"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.loggedIn", is(false)));
    }

    @Test
    void me_afterFormLogin_returnsUserInfoInSameSession() throws Exception {
        registerAlice();

        // Login and capture the session
        MvcResult loginResult = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", EMAIL)
                .param("password", PASSWORD))
            .andExpect(status().isOk())
            .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        // /me with the same session must be authenticated
        mvc.perform(get("/api/auth/me")
                .session(session)
                .header("X-Requested-With", "XMLHttpRequest"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.loggedIn", is(true)))
            .andExpect(jsonPath("$.email", is(EMAIL)))
            .andExpect(jsonPath("$.searchCount").isNumber());
    }

    @Test
    void me_withOAuth2Session_returnsLoggedInTrue() throws Exception {
        // Simulates a user who authenticated via Google OAuth2
        mvc.perform(get("/api/auth/me")
                .with(oauth2Login()
                    .attributes(attrs -> {
                        attrs.put("email", "google-user@gmail.com");
                        attrs.put("name",  "Google User");
                    }))
                .header("X-Requested-With", "XMLHttpRequest"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.loggedIn", is(true)))
            .andExpect(jsonPath("$.email", is("google-user@gmail.com")));
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Test
    void logout_invalidatesSession() throws Exception {
        registerAlice();

        MvcResult loginResult = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", EMAIL)
                .param("password", PASSWORD))
            .andExpect(status().isOk())
            .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mvc.perform(post("/api/auth/logout")
                .session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.loggedOut", is(true)));

        // Session must be invalidated — /me should now return 401
        mvc.perform(get("/api/auth/me")
                .session(session)
                .header("X-Requested-With", "XMLHttpRequest"))
            .andExpect(status().isUnauthorized());
    }

    // ── Google OAuth2 redirect ────────────────────────────────────────────────

    @Test
    void googleOAuth_authorizationRequest_redirectsToGoogle() throws Exception {
        mvc.perform(get("/oauth2/authorization/google"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", containsString("accounts.google.com")));
    }

    @Test
    void googleOAuth_authorizationRequest_setsCookieWithState() throws Exception {
        // The oauth2_auth_req cookie must be set so the callback can validate state
        MvcResult result = mvc.perform(get("/oauth2/authorization/google"))
            .andReturn();

        Cookie cookie = result.getResponse().getCookie("oauth2_auth_req");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isNotEmpty();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getMaxAge()).isGreaterThan(0);
    }

    @Test
    void googleOAuth_authorizationRequest_overHTTPS_setCookieIsSecure() throws Exception {
        // When request arrives over HTTPS (X-Forwarded-Proto set by Railway/Cloudflare),
        // the state cookie must carry Secure+SameSite=None so it survives the cross-site
        // redirect from Google back to the app.
        MvcResult result = mvc.perform(get("/oauth2/authorization/google")
                .header("X-Forwarded-Proto", "https")
                .secure(true))
            .andReturn();

        String setCookieHeader = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookieHeader).isNotNull();
        assertThat(setCookieHeader).containsIgnoringCase("Secure");
        assertThat(setCookieHeader).containsIgnoringCase("SameSite=None");
    }

    // ── Search-count / paywall guard ──────────────────────────────────────────

    @Test
    void searchIncrement_unauthenticated_redirectsToLogin() throws Exception {
        // Without an AJAX header Spring Security redirects to /login.html (302)
        // rather than returning 401 JSON — confirm the request is rejected either way
        mvc.perform(post("/api/auth/search/increment")
                .header("X-Requested-With", "XMLHttpRequest"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void searchIncrement_authenticated_returnsNewCount() throws Exception {
        registerAlice();

        MvcResult loginResult = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", EMAIL)
                .param("password", PASSWORD))
            .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mvc.perform(post("/api/auth/search/increment").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.searchCount", is(1)));

        mvc.perform(post("/api/auth/search/increment").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.searchCount", is(2)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void registerAlice() throws Exception {
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(NAME, EMAIL, PASSWORD)))
            .andExpect(status().isOk());
    }

    private static String json(String name, String email, String password) {
        return String.format(
            "{\"name\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}",
            name, email, password);
    }
}
