package com.fan.lazyday.infrastructure.filter;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Locale;

/**
 * @author chenbin
 */
@Slf4j
@Component
public class AppWebFilter implements WebFilter {

    public AppWebFilter() {
    }

    @NonNull
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        final ServerHttpRequest request = exchange.getRequest();
        final ServerHttpResponse response = exchange.getResponse();

        StopWatch sw = null;

        RequestPath requestPath = request.getPath();
        if ("/actuator/health".equalsIgnoreCase(requestPath.value())) {
            sw = StopWatch.createStarted();
        }

        LocaleContext localeContext = exchange.getLocaleContext();
        Locale locale = localeContext.getLocale();

      /*  UserInfo userInfo = RequestHelper.isWebSocket(request) ?
                UserInfo.WS_USER :
                UserInfo.fromRequest(request, locale).validate();*/

        final StopWatch finalSw = sw;

        return chain.filter(exchange)
              //  .contextWrite(ContextHolders.write(request, response, userInfo, null, locale))
                .doOnTerminate(() -> {
                    if (finalSw != null) {
                        finalSw.stop();
                        if (finalSw.getTime() > 200) {
                            log.info("call health check requestId: {}, remote: {}, 接口耗时: {} ms", request.getId(), request.getRemoteAddress(), finalSw.getTime());
                        }
                    }
                });
    }
}
