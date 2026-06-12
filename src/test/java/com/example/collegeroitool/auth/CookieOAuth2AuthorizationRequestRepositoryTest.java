package com.example.collegeroitool.auth;

import com.example.collegeroitool.config.CookieOAuth2AuthorizationRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CookieOAuth2AuthorizationRequestRepository.
 *
 * Specifically covers the HTTPS cookie-clear bug fixed in the auth work:
 * removeAuthorizationRequest must set Secure+SameSite=None when clearing on
 * HTTPS, otherwise the browser ignores the clear and the stale state cookie
 * breaks subsequent Google sign-in attempts.
 */
class CookieOAuth2AuthorizationRequestRepositoryTest {

    private CookieOAuth2AuthorizationRequestRepository repo;
    private OAuth2AuthorizationRequest sampleRequest;

    @BeforeEach
    void setUp() {
        repo = new CookieOAuth2AuthorizationRequestRepository();
        sampleRequest = OAuth2AuthorizationRequest.authorizationCode()
            .clientId("test-client")
            .authorizationUri("https://accounts.google.com/o/oauth2/auth")
            .redirectUri("https://app.example.com/login/oauth2/code/google")
            .scopes(java.util.Set.of("openid", "email", "profile"))
            .state("test-state-abc123")
            .additionalParameters(Map.of("nonce", "test-nonce"))
            .build();
    }

    // ── save / load round-trip ────────────────────────────────────────────────

    @Test
    void save_andLoad_roundTrip_returnsOriginalRequest() {
        MockHttpServletRequest  req  = httpRequest(false);
        MockHttpServletResponse res  = new MockHttpServletResponse();

        repo.saveAuthorizationRequest(sampleRequest, req, res);

        // Inject the cookie the response set back into a new request
        MockHttpServletRequest callback = httpRequestWithCookie(res, false);
        OAuth2AuthorizationRequest loaded = repo.loadAuthorizationRequest(callback);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getState()).isEqualTo("test-state-abc123");
        assertThat(loaded.getClientId()).isEqualTo("test-client");
    }

    @Test
    void save_overHTTP_doesNotSetSecureFlag() {
        MockHttpServletRequest  req = httpRequest(false);
        MockHttpServletResponse res = new MockHttpServletResponse();

        repo.saveAuthorizationRequest(sampleRequest, req, res);

        String setCookie = res.getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        assertThat(setCookie.toLowerCase()).doesNotContain("secure");
        assertThat(setCookie.toLowerCase()).doesNotContain("samesite=none");
    }

    @Test
    void save_overHTTPS_setsSecureAndSameSiteNone() {
        MockHttpServletRequest  req = httpRequest(true);
        MockHttpServletResponse res = new MockHttpServletResponse();

        repo.saveAuthorizationRequest(sampleRequest, req, res);

        String setCookie = res.getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).containsIgnoringCase("Secure");
        assertThat(setCookie).containsIgnoringCase("SameSite=None");
    }

    // ── remove (the bug we fixed) ─────────────────────────────────────────────

    @Test
    void remove_overHTTPS_clearsCookieWithSecureFlag() {
        // Save on HTTPS
        MockHttpServletRequest  saveReq = httpRequest(true);
        MockHttpServletResponse saveRes = new MockHttpServletResponse();
        repo.saveAuthorizationRequest(sampleRequest, saveReq, saveRes);

        // Simulate callback: carry cookie into removal request over HTTPS
        MockHttpServletRequest  removeReq = httpRequestWithCookie(saveRes, true);
        MockHttpServletResponse removeRes = new MockHttpServletResponse();

        OAuth2AuthorizationRequest removed = repo.removeAuthorizationRequest(removeReq, removeRes);

        assertThat(removed).isNotNull();
        String clearCookie = removeRes.getHeader("Set-Cookie");
        assertThat(clearCookie).isNotNull();
        // Must include Secure so the browser replaces/expires the Secure cookie it stored
        assertThat(clearCookie).containsIgnoringCase("Secure");
        assertThat(clearCookie).containsIgnoringCase("SameSite=None");
        assertThat(clearCookie).containsIgnoringCase("Max-Age=0");
    }

    @Test
    void remove_overHTTP_clearsCookieWithoutSecureFlag() {
        MockHttpServletRequest  saveReq = httpRequest(false);
        MockHttpServletResponse saveRes = new MockHttpServletResponse();
        repo.saveAuthorizationRequest(sampleRequest, saveReq, saveRes);

        MockHttpServletRequest  removeReq = httpRequestWithCookie(saveRes, false);
        MockHttpServletResponse removeRes = new MockHttpServletResponse();

        repo.removeAuthorizationRequest(removeReq, removeRes);

        String clearCookie = removeRes.getHeader("Set-Cookie");
        assertThat(clearCookie).isNotNull();
        assertThat(clearCookie.toLowerCase()).doesNotContain("secure");
        assertThat(clearCookie).containsIgnoringCase("Max-Age=0");
    }

    @Test
    void remove_noCookiePresent_returnsNullAndDoesNotClear() {
        MockHttpServletRequest  req = httpRequest(true);
        MockHttpServletResponse res = new MockHttpServletResponse();

        OAuth2AuthorizationRequest result = repo.removeAuthorizationRequest(req, res);

        assertThat(result).isNull();
        assertThat(res.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void saveNull_clearsCookie() {
        MockHttpServletRequest  req = httpRequest(true);
        MockHttpServletResponse res = new MockHttpServletResponse();

        repo.saveAuthorizationRequest(null, req, res);

        String setCookie = res.getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).containsIgnoringCase("Max-Age=0");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MockHttpServletRequest httpRequest(boolean https) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setScheme(https ? "https" : "http");
        req.setSecure(https);
        if (https) req.addHeader("X-Forwarded-Proto", "https");
        return req;
    }

    private MockHttpServletRequest httpRequestWithCookie(MockHttpServletResponse saveRes, boolean https) {
        MockHttpServletRequest req = httpRequest(https);
        // Extract the Set-Cookie value and inject it as a Cookie header
        String setCookie = saveRes.getHeader("Set-Cookie");
        if (setCookie != null) {
            String[] parts = setCookie.split(";");
            String nameValue = parts[0].trim();
            int eq = nameValue.indexOf('=');
            if (eq > 0) {
                String name  = nameValue.substring(0, eq);
                String value = nameValue.substring(eq + 1);
                req.setCookies(new jakarta.servlet.http.Cookie(name, value));
            }
        }
        return req;
    }
}
