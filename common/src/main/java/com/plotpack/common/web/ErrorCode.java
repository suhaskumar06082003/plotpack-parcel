package com.plotpack.common.web;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST),
    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND),
    TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    ALREADY_DELIVERED(HttpStatus.CONFLICT),
    MAX_BINS_PER_CUSTOMER(HttpStatus.UNPROCESSABLE_ENTITY),
    NO_FREE_BIN(HttpStatus.SERVICE_UNAVAILABLE),
    ALLOCATION_BUSY(HttpStatus.SERVICE_UNAVAILABLE),
    DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
