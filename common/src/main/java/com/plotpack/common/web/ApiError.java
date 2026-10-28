package com.plotpack.common.web;

import java.time.Instant;

public record ApiError(String code, String message, int status, Instant timestamp) {
    public static ApiError of(ErrorCode code, String message) {
        return new ApiError(code.name(), message, code.status().value(), Instant.now());
    }
}
