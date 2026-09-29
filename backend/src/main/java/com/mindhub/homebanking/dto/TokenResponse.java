package com.mindhub.homebanking.dto;

import com.mindhub.homebanking.domain.Role;

/** Access token returned in the body; the refresh token travels only in an HttpOnly cookie. */
public record TokenResponse(String accessToken, String tokenType, long expiresIn, Role role) {

    public static TokenResponse bearer(String accessToken, long expiresInSeconds, Role role) {
        return new TokenResponse(accessToken, "Bearer", expiresInSeconds, role);
    }
}
