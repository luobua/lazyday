package com.fan.lazyday.infrastructure.exception;

import com.fan.lazyday.interfaces.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.support.WebExchangeBindException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * GlobalExceptionHandler 单元测试
 * 覆盖：BizException、ValidationException、通用 Exception
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("handleBizException: UNAUTHORIZED")
    void handleBizException_unauthorized_shouldReturnCorrectResponse() {
        BizException ex = BizException.unauthorized("TOKEN_EXPIRED", "令牌已过期");

        ResponseEntity<ApiResponse<Void>> response = handler.handleBizException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ApiResponse<Void> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo(40101); // 401 * 100 + 1
        assertThat(body.getErrorCode()).isEqualTo("TOKEN_EXPIRED");
        assertThat(body.getMessage()).isEqualTo("令牌已过期");
        assertThat(body.getRequestId()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("handleBizException: NOT_FOUND")
    void handleBizException_notFound_shouldReturnCorrectResponse() {
        BizException ex = BizException.notFound("USER_NOT_FOUND", "用户不存在");

        ResponseEntity<ApiResponse<Void>> response = handler.handleBizException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getCode()).isEqualTo(40401);
        assertThat(response.getBody().getErrorCode()).isEqualTo("USER_NOT_FOUND");
    }

    @Test
    @DisplayName("handleBizException: FORBIDDEN")
    void handleBizException_forbidden_shouldReturnCorrectResponse() {
        BizException ex = BizException.forbidden("FORBIDDEN_ROLE", "权限不足");

        ResponseEntity<ApiResponse<Void>> response = handler.handleBizException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getCode()).isEqualTo(40301);
    }

    @Test
    @DisplayName("handleBizException: CONFLICT")
    void handleBizException_conflict_shouldReturnCorrectResponse() {
        BizException ex = BizException.conflict("DUPLICATE_USERNAME", "用户名已被使用");

        ResponseEntity<ApiResponse<Void>> response = handler.handleBizException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getCode()).isEqualTo(40901);
    }

    @Test
    @DisplayName("handleBizException: BAD_REQUEST")
    void handleBizException_badRequest_shouldReturnCorrectResponse() {
        BizException ex = BizException.badRequest("INVALID_PARAM", "参数无效");

        ResponseEntity<ApiResponse<Void>> response = handler.handleBizException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo(40001);
    }

    @Test
    @DisplayName("handleValidationException: 单字段错误")
    void handleValidationException_singleFieldError() {
        WebExchangeBindException ex = mock(WebExchangeBindException.class);
        FieldError fieldError = new FieldError("request", "username", "不能为空");
        when(ex.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiResponse<Void> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo(40000);
        assertThat(body.getErrorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.getMessage()).isEqualTo("username: 不能为空");
    }

    @Test
    @DisplayName("handleValidationException: 多字段错误")
    void handleValidationException_multipleFieldErrors() {
        WebExchangeBindException ex = mock(WebExchangeBindException.class);
        FieldError fe1 = new FieldError("request", "username", "不能为空");
        FieldError fe2 = new FieldError("request", "email", "格式不正确");
        when(ex.getFieldErrors()).thenReturn(List.of(fe1, fe2));

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidationException(ex);

        ApiResponse<Void> body = response.getBody();
        assertThat(body.getMessage()).contains("username: 不能为空");
        assertThat(body.getMessage()).contains("email: 格式不正确");
    }

    @Test
    @DisplayName("handleException: 通用异常 → 500")
    void handleException_generic_shouldReturn500() {
        Exception ex = new RuntimeException("Something went wrong");

        ResponseEntity<ApiResponse<Void>> response = handler.handleException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ApiResponse<Void> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo(50000);
        assertThat(body.getErrorCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(body.getMessage()).isEqualTo("服务器内部错误");
        assertThat(body.getRequestId()).isNotNull();
    }
}
