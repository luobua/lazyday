package com.fan.lazyday.interfaces.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private int code;

    @JsonProperty("error_code")
    private String errorCode;

    private String message;

    private T data;

    @JsonProperty("request_id")
    private String requestId;

    public static <T> ApiResponse<T> success(T data, String requestId) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(0);
        response.setMessage("success");
        response.setData(data);
        response.setRequestId(requestId);
        return response;
    }

    public static <T> ApiResponse<T> error(int code, String errorCode, String message, String requestId) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(code);
        response.setErrorCode(errorCode);
        response.setMessage(message);
        response.setRequestId(requestId);
        return response;
    }
}