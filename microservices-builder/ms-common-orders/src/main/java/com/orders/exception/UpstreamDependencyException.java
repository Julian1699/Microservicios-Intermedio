package com.orders.exception;

import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class UpstreamDependencyException extends ResponseStatusException {

    private final String service;

    public UpstreamDependencyException(
            HttpStatus status,
            String reason,
            String service,
            Throwable cause) {
        super(status, reason, cause);
        this.service = Objects.requireNonNull(service);
    }

    public String getDependencyService() {
        return service;
    }

}
