package com.mindhub.homebanking.dto;

import java.time.LocalDateTime;

/** @param link route of the app to open, e.g. {@code /accounts/5} (may be null) */
public record NotificationDTO(Long id, String type, String title, String message, String link,
                              LocalDateTime createdAt, boolean read) {
}
