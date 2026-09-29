package com.mindhub.homebanking.service;

import com.mindhub.homebanking.domain.Client;
import com.mindhub.homebanking.dto.TokenResponse;
import com.mindhub.homebanking.repository.ClientRepository;
import com.mindhub.homebanking.security.AccessTokenService;
import com.mindhub.homebanking.security.RefreshTokenService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    /** Access token for the response body plus the raw refresh token for the HttpOnly cookie. */
    public record Session(TokenResponse token, String refreshToken) {
    }

    private final AuthenticationManager authenticationManager;
    private final ClientRepository clientRepository;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(AuthenticationManager authenticationManager, ClientRepository clientRepository,
                       AccessTokenService accessTokenService, RefreshTokenService refreshTokenService) {
        this.authenticationManager = authenticationManager;
        this.clientRepository = clientRepository;
        this.accessTokenService = accessTokenService;
        this.refreshTokenService = refreshTokenService;
    }

    /** @throws org.springframework.security.core.AuthenticationException on bad credentials */
    public Session login(String email, String password) {
        authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, password));
        Client client = clientRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("Bad credentials"));
        return new Session(accessToken(client), refreshTokenService.issue(client));
    }

    public Session refresh(String rawRefreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken);
        return new Session(accessToken(rotation.client()), rotation.rawToken());
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revoke(rawRefreshToken);
        }
    }

    private TokenResponse accessToken(Client client) {
        return TokenResponse.bearer(accessTokenService.issue(client), accessTokenService.ttlSeconds(), client.getRole());
    }
}
