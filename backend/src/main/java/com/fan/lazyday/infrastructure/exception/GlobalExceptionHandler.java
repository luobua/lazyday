package com.fan.lazyday.infrastructure.exception;

import com.fan.lazyday.interfaces.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBizException(BizException ex) {
        HttpStatus status = ErrorCode.fromCode(ex.getErrorCode())
                .map(ErrorCode::getHttpStatus)
                .orElse(ex.getHttpStatus());
        String requestId = UUID.randomUUID().toString();
        ApiResponse<Void> response = ApiResponse.error(
                status.value() * 100 + ex.getBizCode(),
                ex.getErrorCode(),
                ex.getMessage(),
                requestId
        );
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(WebExchangeBindException ex) {
        String requestId = UUID.randomUUID().toString();
        String details = ex.getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        ApiResponse<Void> response = ApiResponse.error(
                40000,
                "VALIDATION_ERROR",
                details,
                requestId
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        String requestId = UUID.randomUUID().toString();
        ApiResponse<Void> response = ApiResponse.error(
                50000,
                "INTERNAL_ERROR",
                "服务器内部错误",
                requestId
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
