package com.fan.lazyday.infrastructure.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BizException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final int bizCode;
    private final String errorCode;

    public BizException(HttpStatus httpStatus, int bizCode, String errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.bizCode = bizCode;
        this.errorCode = errorCode;
    }

    public static BizException of(ErrorCode errorCode, String message) {
        return new BizException(errorCode.getHttpStatus(), 1, errorCode.getCode(), message);
    }

    public static BizException unauthorized(String errorCode, String message) {
        return new BizException(HttpStatus.UNAUTHORIZED, 1, errorCode, message);
    }

    public static BizException forbidden(String errorCode, String message) {
        return new BizException(HttpStatus.FORBIDDEN, 1, errorCode, message);
    }

    public static BizException notFound(String errorCode, String message) {
        return new BizException(HttpStatus.NOT_FOUND, 1, errorCode, message);
    }

    public static BizException conflict(String errorCode, String message) {
        return new BizException(HttpStatus.CONFLICT, 1, errorCode, message);
    }

    public static BizException badRequest(String errorCode, String message) {
        return new BizException(HttpStatus.BAD_REQUEST, 1, errorCode, message);
    }
}
