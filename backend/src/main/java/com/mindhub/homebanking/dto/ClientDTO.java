package com.mindhub.homebanking.dto;

import com.mindhub.homebanking.domain.Role;

import java.util.List;

/**
 * @param enabled          false when blocked by an administrator
 * @param locked           true while temporarily locked after too many failed logins
 * @param twoFactorEnabled true when sign-in and large transfers require an authenticator app code
 */
public record ClientDTO(Long id, String name, String lastName, String email, Role role, boolean enabled,
                        boolean locked, boolean twoFactorEnabled, List<AccountDTO> accounts) {
}
