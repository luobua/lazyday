package com.fan.lazyday.infrastructure.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BizException 单元测试
 * 验证各工厂方法的 httpStatus/bizCode/errorCode 组合
 */
class BizExceptionTest {

    @Test
    @DisplayName("unauthorized: 应返回 401 状态码")
    void unauthorized_shouldReturn401() {
        BizException ex = BizException.unauthorized("INVALID_TOKEN", "令牌无效");

        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getBizCode()).isEqualTo(1);
        assertThat(ex.getErrorCode()).isEqualTo("INVALID_TOKEN");
        assertThat(ex.getMessage()).isEqualTo("令牌无效");
    }

    @Test
    @DisplayName("forbidden: 应返回 403 状态码")
    void forbidden_shouldReturn403() {
        BizException ex = BizException.forbidden("FORBIDDEN_ROLE", "权限不足");

        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getBizCode()).isEqualTo(1);
        assertThat(ex.getErrorCode()).isEqualTo("FORBIDDEN_ROLE");
        assertThat(ex.getMessage()).isEqualTo("权限不足");
    }

    @Test
    @DisplayName("notFound: 应返回 404 状态码")
    void notFound_shouldReturn404() {
        BizException ex = BizException.notFound("USER_NOT_FOUND", "用户不存在");

        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getBizCode()).isEqualTo(1);
        assertThat(ex.getErrorCode()).isEqualTo("USER_NOT_FOUND");
        assertThat(ex.getMessage()).isEqualTo("用户不存在");
    }

    @Test
    @DisplayName("conflict: 应返回 409 状态码")
    void conflict_shouldReturn409() {
        BizException ex = BizException.conflict("DUPLICATE_USERNAME", "用户名已存在");

        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getBizCode()).isEqualTo(1);
        assertThat(ex.getErrorCode()).isEqualTo("DUPLICATE_USERNAME");
        assertThat(ex.getMessage()).isEqualTo("用户名已存在");
    }

    @Test
    @DisplayName("badRequest: 应返回 400 状态码")
    void badRequest_shouldReturn400() {
        BizException ex = BizException.badRequest("INVALID_PARAM", "参数错误");

        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getBizCode()).isEqualTo(1);
        assertThat(ex.getErrorCode()).isEqualTo("INVALID_PARAM");
        assertThat(ex.getMessage()).isEqualTo("参数错误");
    }

    @Test
    @DisplayName("BizException 应是 RuntimeException 的子类")
    void shouldExtendRuntimeException() {
        BizException ex = BizException.unauthorized("TEST", "test");

        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("构造函数: 自定义 bizCode")
    void constructor_customBizCode() {
        BizException ex = new BizException(HttpStatus.BAD_REQUEST, 42, "CUSTOM", "自定义错误");

        assertThat(ex.getBizCode()).isEqualTo(42);
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getErrorCode()).isEqualTo("CUSTOM");
    }
}
