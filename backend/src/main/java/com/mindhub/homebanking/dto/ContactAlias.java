package com.mindhub.homebanking.dto;

/**
 * Aliases: letters (any language), digits, spaces and . - _ ; must start with a letter or digit.
 * Surrounding and repeated spaces are accepted here and normalized by the service.
 */
public final class ContactAlias {

    public static final String PATTERN = "^\\s*[\\p{L}\\p{N}][\\p{L}\\p{N} ._\\-]*$";
    public static final String MESSAGE = "may contain letters, digits, spaces and . - _";

    private ContactAlias() {
    }
}
