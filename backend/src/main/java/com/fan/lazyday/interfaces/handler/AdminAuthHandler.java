package com.fan.lazyday.interfaces.handler;

import com.fan.lazyday.application.facade.AuthFacade;
import com.fan.lazyday.infrastructure.config.path.RequestMappingAdminV1;
import com.fan.lazyday.infrastructure.filter.RequestIdFilter;
import com.fan.lazyday.infrastructure.security.CookieUtils;
import com.fan.lazyday.infrastructure.security.JwtService;
import com.fan.lazyday.infrastructure.security.TenantContext;
import com.fan.lazyday.interfaces.api.AdminAuthApi;
import com.fan.lazyday.interfaces.request.LoginRequest;
import com.fan.lazyday.interfaces.response.ApiResponse;
import com.fan.lazyday.interfaces.response.CsrfTokenResponse;
import com.fan.lazyday.interfaces.response.UserInfoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMappingAdminV1
@RequiredArgsConstructor
public class AdminAuthHandler implements AdminAuthApi {

    private final AuthFacade authFacade;
    private final JwtService jwtService;

    @Override
    public Mono<ApiResponse<UserInfoResponse>> login(Mono<LoginRequest> request, ServerWebExchange exchange) {
        return request.flatMap(req ->
                authFacade.adminLogin(req.getUsername(), req.getPassword())
                        .flatMap(userInfo -> {
                            String accessToken = jwtService.generateAccessToken(userInfo.getId(), userInfo.getTenantId(), userInfo.getRole());
                            String refreshToken = jwtService.generateRefreshToken(userInfo.getId(), userInfo.getTenantId(), userInfo.getRole(), false);
                            exchange.getResponse().addCookie(
                                    CookieUtils.createAccessTokenCookie(accessToken, jwtService.getAccessTokenExpiry()));
                            exchange.getResponse().addCookie(
                                    CookieUtils.createRefreshTokenCookie(refreshToken, jwtService.getRefreshTokenExpiry(false)));
                            return wrapSuccess(userInfo);
                        })
        );
    }

    @Override
    public Mono<ApiResponse<Void>> logout(ServerWebExchange exchange) {
        exchange.getResponse().addCookie(CookieUtils.clearCookie(CookieUtils.ACCESS_TOKEN_COOKIE));
        exchange.getResponse().addCookie(CookieUtils.clearCookie(CookieUtils.REFRESH_TOKEN_COOKIE));
        return wrapSuccess(null);
    }

    @Override
    public Mono<ApiResponse<UserInfoResponse>> me() {
        return TenantContext.current()
                .flatMap(ctx -> authFacade.getUserInfo(ctx.getUserId()))
                .flatMap(this::wrapSuccess);
    }

    @Override
    public Mono<ApiResponse<CsrfTokenResponse>> csrfToken(ServerWebExchange exchange) {
        String token = UUID.randomUUID().toString();
        exchange.getResponse().addCookie(CookieUtils.createCsrfTokenCookie(token));
        return wrapSuccess(new CsrfTokenResponse(token));
    }

    private <T> Mono<ApiResponse<T>> wrapSuccess(T data) {
        return RequestIdFilter.getRequestId()
                .defaultIfEmpty("unknown")
                .map(requestId -> ApiResponse.success(data, requestId));
    }
}
