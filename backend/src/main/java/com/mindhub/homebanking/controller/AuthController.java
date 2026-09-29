package com.mindhub.homebanking.controller;

import com.mindhub.homebanking.config.SecurityProperties;
import com.mindhub.homebanking.dto.LoginRequest;
import com.mindhub.homebanking.dto.TokenResponse;
import com.mindhub.homebanking.exception.InvalidRefreshTokenException;
import com.mindhub.homebanking.security.LoginRateLimiter;
import com.mindhub.homebanking.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String COOKIE_PATH = "/api/auth";

    private final AuthService authService;
    private final LoginRateLimiter loginRateLimiter;
    private final SecurityProperties properties;

    public AuthController(AuthService authService, LoginRateLimiter loginRateLimiter, SecurityProperties properties) {
        this.authService = authService;
        this.loginRateLimiter = loginRateLimiter;
        this.properties = properties;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        // Remote address honours X-Forwarded-For only via server.forward-headers-strategy (trusted proxy).
        loginRateLimiter.consume(http.getRemoteAddr());
        AuthService.Session session = authService.login(request.email(), request.password());
        return withRefreshCookie(session);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = "${app.security.refresh-token.cookie-name}", required = false) String refreshToken,
            @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin) {
        requireTrustedOrigin(origin);
        return withRefreshCookie(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "${app.security.refresh-token.cookie-name}", required = false) String refreshToken,
            @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin) {
        requireTrustedOrigin(origin);
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    private ResponseEntity<TokenResponse> withRefreshCookie(AuthService.Session session) {
        ResponseCookie cookie = refreshCookie(session.refreshToken(), properties.refreshToken().ttl());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(session.token());
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(properties.refreshToken().cookieName(), value)
                .httpOnly(true)
                .secure(properties.refreshToken().cookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    /**
     * Defence in depth for the cookie-authenticated endpoints (CSRF): browsers always send Origin on
     * POST, so a present Origin must be this server or one of the configured front-end origins.
     */
    private void requireTrustedOrigin(String origin) {
        if (origin == null) {
            return; // non-browser client (curl, tests): no ambient cookie risk
        }
        String self = ServletUriComponentsBuilder.fromCurrentContextPath().replacePath(null).build().toUriString();
        if (!origin.equals(self) && !properties.cors().allowedOrigins().contains(origin)) {
            throw new InvalidRefreshTokenException("Untrusted origin");
        }
    }
}
