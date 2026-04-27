package com.fan.lazyday.interfaces.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ApiResponse 单元测试
 * 验证 success/error 工厂方法
 */
class ApiResponseTest {

    @Test
    @DisplayName("success: 成功响应应包含正确字段")
    void success_shouldContainCorrectFields() {
        String requestId = "req-123";
        ApiResponse<String> response = ApiResponse.success("hello", requestId);

        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getMessage()).isEqualTo("success");
        assertThat(response.getData()).isEqualTo("hello");
        assertThat(response.getRequestId()).isEqualTo(requestId);
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    @DisplayName("success: data 为 null 时应正常工作")
    void success_nullData_shouldWork() {
        ApiResponse<Void> response = ApiResponse.success(null, "req-456");

        assertThat(response.getCode()).isEqualTo(0);
        assertThat(response.getData()).isNull();
    }

    @Test
    @DisplayName("error: 错误响应应包含正确字段")
    void error_shouldContainCorrectFields() {
        ApiResponse<Void> response = ApiResponse.error(40100, "UNAUTHORIZED", "未登录", "req-789");

        assertThat(response.getCode()).isEqualTo(40100);
        assertThat(response.getErrorCode()).isEqualTo("UNAUTHORIZED");
        assertThat(response.getMessage()).isEqualTo("未登录");
        assertThat(response.getRequestId()).isEqualTo("req-789");
        assertThat(response.getData()).isNull();
    }

    @Test
    @DisplayName("error: GlobalExceptionHandler 使用的 error code 计算公式")
    void error_codeShouldFollowConvention() {
        // GlobalExceptionHandler 使用 httpStatus.value() * 100 + 1
        ApiResponse<Void> response401 = ApiResponse.error(40101, "UNAUTHORIZED", "test", "r1");
        ApiResponse<Void> response403 = ApiResponse.error(40301, "FORBIDDEN_ROLE", "test", "r2");
        ApiResponse<Void> response404 = ApiResponse.error(40401, "NOT_FOUND", "test", "r3");
        ApiResponse<Void> response500 = ApiResponse.error(50001, "INTERNAL_ERROR", "test", "r4");

        assertThat(response401.getCode()).isEqualTo(40101);
        assertThat(response403.getCode()).isEqualTo(40301);
        assertThat(response404.getCode()).isEqualTo(40401);
        assertThat(response500.getCode()).isEqualTo(50001);
    }
}
