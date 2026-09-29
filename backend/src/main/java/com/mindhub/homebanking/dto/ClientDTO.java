package com.mindhub.homebanking.dto;

import com.mindhub.homebanking.domain.Role;

import java.util.List;

public record ClientDTO(Long id, String name, String lastName, String email, Role role, List<AccountDTO> accounts) {
}
