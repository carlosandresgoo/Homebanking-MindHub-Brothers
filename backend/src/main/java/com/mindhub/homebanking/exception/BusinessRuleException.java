package com.mindhub.homebanking.exception;

import java.util.Map;

/**
 * A well-formed request that breaks a business rule (e.g. insufficient funds). Mapped to 422; the
 * optional properties (e.g. {@code code}, {@code remaining}) are added to the problem response.
 */
public class BusinessRuleException extends RuntimeException {

    private final transient Map<String, Object> properties;

    public BusinessRuleException(String message) {
        this(message, Map.of());
    }

    public BusinessRuleException(String message, Map<String, Object> properties) {
        super(message);
        this.properties = Map.copyOf(properties);
    }

    public Map<String, Object> getProperties() {
        return properties;
    }
}
