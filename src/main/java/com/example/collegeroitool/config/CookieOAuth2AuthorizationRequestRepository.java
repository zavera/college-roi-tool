package com.example.collegeroitool.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.io.*;
import java.util.Base64;

/**
 * Stores the OAuth2 authorization request (including state) in a short-lived cookie
 * instead of the HTTP session. This survives the cross-site redirect from Google back
 * to the app even when Cloudflare or other proxies interfere with session cookies.
 * The cookie is SameSite=None; Secure so it is always sent on the callback redirect.
 */
public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final Logger log = LoggerFactory.getLogger(CookieOAuth2AuthorizationRequestRepository.class);
    private static final String COOKIE_NAME = "oauth2_auth_req";
    private static final int MAX_AGE_SECONDS = 180;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return readCookie(request);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request, HttpServletResponse response) {
        boolean https = "https".equalsIgnoreCase(request.getScheme())
                || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
        currentRequestIsHttps.set(https);
        if (authorizationRequest == null) {
            clearCookie(response);
            return;
        }
        log.info("[OAuth2Cookie] SAVE state={} https={} scheme={} x-forwarded-proto={}",
                authorizationRequest.getState(), https,
                request.getScheme(), request.getHeader("X-Forwarded-Proto"));
        response.addHeader("Set-Cookie", buildCookieHeader(
                COOKIE_NAME, serialize(authorizationRequest), MAX_AGE_SECONDS));
        currentRequestIsHttps.remove();
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
            HttpServletResponse response) {
        OAuth2AuthorizationRequest req = loadAuthorizationRequest(request);
        boolean https = "https".equalsIgnoreCase(request.getScheme())
                || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
        if (req != null) {
            log.info("[OAuth2Cookie] REMOVE (callback) state={} https={} scheme={} x-forwarded-proto={}",
                    req.getState(), https,
                    request.getScheme(), request.getHeader("X-Forwarded-Proto"));
            // Must set the same Secure+SameSite=None flag used when saving, otherwise
            // browsers on HTTPS treat it as a different cookie and ignore the clear.
            currentRequestIsHttps.set(https);
            clearCookie(response);
            currentRequestIsHttps.remove();
        } else {
            // Cookie was missing or couldn't be deserialized — Spring will reject the callback
            Cookie[] cookies = request.getCookies();
            int cookieCount = (cookies == null) ? 0 : cookies.length;
            log.warn("[OAuth2Cookie] REMOVE — cookie NOT found (totalCookies={} https={}) — state mismatch will follow",
                    cookieCount, https);
        }
        return req;
    }

    private OAuth2AuthorizationRequest readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName())) {
                OAuth2AuthorizationRequest req = deserialize(c.getValue());
                if (req == null) {
                    log.warn("[OAuth2Cookie] READ — cookie present but deserialization failed (value length={})",
                            c.getValue().length());
                }
                return req;
            }
        }
        return null;
    }

    private void clearCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie", buildCookieHeader(COOKIE_NAME, "", 0));
    }

    private String buildCookieHeader(String name, String value, int maxAge) {
        // Secure + SameSite=None required for cross-site redirect over HTTPS (prod).
        // Over plain HTTP (local dev) the Secure flag causes the browser to drop the
        // cookie silently, breaking the Google OAuth callback state check.
        boolean secure = currentRequestIsHttps.get();
        String header = name + "=" + value
                + "; Path=/"
                + "; HttpOnly"
                + "; Max-Age=" + maxAge;
        if (secure) header += "; Secure; SameSite=None";
        return header;
    }

    // Populated by saveAuthorizationRequest so clearCookie/buildCookieHeader can read it.
    private final ThreadLocal<Boolean> currentRequestIsHttps = ThreadLocal.withInitial(() -> false);

    private String serialize(OAuth2AuthorizationRequest req) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(req);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize OAuth2AuthorizationRequest", e);
        }
    }

    private OAuth2AuthorizationRequest deserialize(String value) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(value);
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                return (OAuth2AuthorizationRequest) ois.readObject();
            }
        } catch (Exception e) {
            return null;
        }
    }
}
