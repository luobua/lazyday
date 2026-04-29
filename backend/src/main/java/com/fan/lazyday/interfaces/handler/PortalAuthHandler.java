package com.fan.lazyday.interfaces.handler;

import com.fan.lazyday.application.facade.AuthFacade;
import com.fan.lazyday.infrastructure.config.path.RequestMappingPortalV1;
import com.fan.lazyday.infrastructure.filter.RequestIdFilter;
import com.fan.lazyday.infrastructure.security.CookieUtils;
import com.fan.lazyday.infrastructure.security.JwtService;
import com.fan.lazyday.infrastructure.security.TenantContext;
import com.fan.lazyday.interfaces.api.PortalAuthApi;
import com.fan.lazyday.interfaces.request.LoginRequest;
import com.fan.lazyday.interfaces.request.RegisterRequest;
import com.fan.lazyday.interfaces.response.ApiResponse;
import com.fan.lazyday.interfaces.response.CsrfTokenResponse;
import com.fan.lazyday.interfaces.response.UserInfoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMappingPortalV1
@RequiredArgsConstructor
public class PortalAuthHandler implements PortalAuthApi {

    private final AuthFacade authFacade;
    private final JwtService jwtService;

    @Override
    public Mono<ApiResponse<UserInfoResponse>> register(Mono<RegisterRequest> request, ServerWebExchange exchange) {
        return request.flatMap(req ->
                authFacade.register(req.getUsername(), req.getEmail(), req.getPassword(), req.getTenantName())
                        .flatMap(userInfo -> {
                            setAuthCookies(exchange, userInfo.getId(), userInfo.getTenantId(), userInfo.getRole(), false);
                            return wrapSuccess(userInfo);
                        })
        );
    }

    @Override
    public Mono<ApiResponse<UserInfoResponse>> login(Mono<LoginRequest> request, ServerWebExchange exchange) {
        return request.flatMap(req ->
                authFacade.login(req.getUsername(), req.getPassword())
                        .flatMap(userInfo -> {
                            setAuthCookies(exchange, userInfo.getId(), userInfo.getTenantId(), userInfo.getRole(), req.isRemember());
                            return wrapSuccess(userInfo);
                        })
        );
    }

    @Override
    public Mono<ApiResponse<Void>> refresh(ServerWebExchange exchange) {
        var cookie = exchange.getRequest().getCookies().getFirst(CookieUtils.REFRESH_TOKEN_COOKIE);
        if (cookie == null || cookie.getValue().isBlank()) {
            return Mono.error(com.fan.lazyday.infrastructure.exception.BizException.unauthorized("REFRESH_TOKEN_EXPIRED", "刷新令牌已过期"));
        }

        JwtService.JwtClaims claims = jwtService.validateToken(cookie.getValue());
        if (claims == null || claims.isExpired()) {
            return Mono.error(com.fan.lazyday.infrastructure.exception.BizException.unauthorized("REFRESH_TOKEN_EXPIRED", "刷新令牌已过期"));
        }

        String accessToken = jwtService.generateAccessToken(claims.getUserId(), claims.getTenantId(), claims.getRole());
        exchange.getResponse().addCookie(
                CookieUtils.createAccessTokenCookie(accessToken, jwtService.getAccessTokenExpiry()));

        return wrapSuccess(null);
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

    @Override
    public Mono<Void> verifyEmail(String token, ServerWebExchange exchange) {
        return authFacade.verifyEmail(token)
                .then(Mono.fromRunnable(() -> {
                    exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                    exchange.getResponse().getHeaders().setLocation(URI.create("/login?verified=1"));
                }));
    }

    private void setAuthCookies(ServerWebExchange exchange, Long userId, Long tenantId, String role, boolean remember) {
        String accessToken = jwtService.generateAccessToken(userId, tenantId, role);
        String refreshToken = jwtService.generateRefreshToken(userId, tenantId, role, remember);
        exchange.getResponse().addCookie(
                CookieUtils.createAccessTokenCookie(accessToken, jwtService.getAccessTokenExpiry()));
        exchange.getResponse().addCookie(
                CookieUtils.createRefreshTokenCookie(refreshToken, jwtService.getRefreshTokenExpiry(remember)));
    }

    private <T> Mono<ApiResponse<T>> wrapSuccess(T data) {
        return RequestIdFilter.getRequestId()
                .defaultIfEmpty("unknown")
                .map(requestId -> ApiResponse.success(data, requestId));
    }
}
