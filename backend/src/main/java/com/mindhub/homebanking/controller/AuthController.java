package com.mindhub.homebanking.controller;

import com.mindhub.homebanking.config.SecurityProperties;
import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.dto.ChangePasswordRequest;
import com.mindhub.homebanking.dto.CreateClientRequest;
import com.mindhub.homebanking.dto.ForgotPasswordRequest;
import com.mindhub.homebanking.dto.LoginRequest;
import com.mindhub.homebanking.dto.ResetPasswordRequest;
import com.mindhub.homebanking.dto.TokenResponse;
import com.mindhub.homebanking.exception.InvalidRefreshTokenException;
import com.mindhub.homebanking.security.LoginRateLimiter;
import com.mindhub.homebanking.service.AuthService;
import com.mindhub.homebanking.service.PasswordService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
    private final PasswordService passwordService;
    private final LoginRateLimiter loginRateLimiter;
    private final SecurityProperties properties;

    public AuthController(AuthService authService, PasswordService passwordService,
                          LoginRateLimiter loginRateLimiter, SecurityProperties properties) {
        this.authService = authService;
        this.passwordService = passwordService;
        this.loginRateLimiter = loginRateLimiter;
        this.properties = properties;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        // Remote address honours X-Forwarded-For only via server.forward-headers-strategy (trusted proxy).
        loginRateLimiter.consume(http.getRemoteAddr());
        AuthService.Session session = authService.login(request.email(), request.password(),
                request.secondFactorCode());
        return withRefreshCookie(session);
    }

    /** Public sign-up (CLIENT role only), rate-limited per IP; logs the new client in (201 + refresh cookie). */
    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody CreateClientRequest request,
                                                  HttpServletRequest http) {
        loginRateLimiter.consume("register:" + http.getRemoteAddr());
        AuthService.Session session = authService.register(request);
        ResponseCookie cookie = refreshCookie(session.refreshToken(), properties.refreshToken().ttl());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(session.token());
    }

    /** Always 202, whether or not the e-mail exists (no account enumeration); rate-limited per IP. */
    @PostMapping("/password/forgot")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                               HttpServletRequest http) {
        loginRateLimiter.consume("forgot:" + http.getRemoteAddr());
        passwordService.requestReset(request.email());
        return ResponseEntity.accepted().build();
    }

    /** 204 on success; 400 for an unknown, expired or already used link. Signs out every session. */
    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request,
                                              HttpServletRequest http) {
        loginRateLimiter.consume("reset:" + http.getRemoteAddr());
        passwordService.reset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /**
     * Logged-in password change: requires the current password, signs out every other session and
     * returns a fresh session for this one.
     */
    @PostMapping("/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TokenResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                        Authentication authentication) {
        Client client = passwordService.change(authentication.getName(), request.currentPassword(),
                request.newPassword());
        return withRefreshCookie(authService.startSession(client));
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
