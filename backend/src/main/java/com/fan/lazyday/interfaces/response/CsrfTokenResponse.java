package com.fan.lazyday.interfaces.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CsrfTokenResponse {
    private String token;
}
