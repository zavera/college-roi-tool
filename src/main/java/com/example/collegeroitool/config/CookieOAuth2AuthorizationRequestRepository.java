package com.example.collegeroitool.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    private static final String COOKIE_NAME = "oauth2_auth_req";
    private static final int MAX_AGE_SECONDS = 180;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return readCookie(request);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            clearCookie(response);
            return;
        }
        response.addHeader("Set-Cookie", buildCookieHeader(
                COOKIE_NAME, serialize(authorizationRequest), MAX_AGE_SECONDS));
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
            HttpServletResponse response) {
        OAuth2AuthorizationRequest req = loadAuthorizationRequest(request);
        if (req != null) clearCookie(response);
        return req;
    }

    private OAuth2AuthorizationRequest readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName())) {
                return deserialize(c.getValue());
            }
        }
        return null;
    }

    private void clearCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie", buildCookieHeader(COOKIE_NAME, "", 0));
    }

    private String buildCookieHeader(String name, String value, int maxAge) {
        return name + "=" + value
                + "; Path=/"
                + "; HttpOnly"
                + "; Secure"
                + "; SameSite=None"
                + "; Max-Age=" + maxAge;
    }

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
