package com.fan.lazyday.infrastructure.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum ErrorCode {
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER"),
    INTERNAL_AUTH_FAILED(HttpStatus.FORBIDDEN, "INTERNAL_AUTH_FAILED"),
    PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND"),
    PLAN_IN_USE(HttpStatus.CONFLICT, "PLAN_IN_USE"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED"),
    QUOTA_DAILY_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "QUOTA_DAILY_EXCEEDED"),
    QUOTA_MONTHLY_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "QUOTA_MONTHLY_EXCEEDED"),
    PARTITION_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "PARTITION_MISSING");

    private final HttpStatus httpStatus;
    private final String code;

    ErrorCode(HttpStatus httpStatus, String code) {
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public static Optional<ErrorCode> fromCode(String code) {
        return Arrays.stream(values())
                .filter(errorCode -> errorCode.code.equals(code))
                .findFirst();
    }
}
