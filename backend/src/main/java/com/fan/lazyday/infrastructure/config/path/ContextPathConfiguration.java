package com.fan.lazyday.infrastructure.config.path;

import com.fan.lazyday.infrastructure.properties.ServiceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.PathMatchConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
@RequiredArgsConstructor
public class ContextPathConfiguration implements WebFluxConfigurer {

    private final ServiceProperties serviceProperties;

    @Override
    public void configurePathMatching(PathMatchConfigurer configure) {
        configure.addPathPrefix(serviceProperties.getContextPathV1(), controllerType ->
                controllerType.isAnnotationPresent(RequestMappingApiV1.class)
        );
        configure.addPathPrefix(serviceProperties.getContextPathV2(), controllerType ->
                controllerType.isAnnotationPresent(RequestMappingApiV2.class)
        );
        configure.addPathPrefix(serviceProperties.getOpenContextPathV1(), controllerType ->
                controllerType.isAnnotationPresent(RequestMappingOpenV1.class)
        );
        configure.addPathPrefix(serviceProperties.getPortalContextPathV1(), controllerType ->
                controllerType.isAnnotationPresent(RequestMappingPortalV1.class)
        );
        configure.addPathPrefix(serviceProperties.getAdminContextPathV1(), controllerType ->
                controllerType.isAnnotationPresent(RequestMappingAdminV1.class)
        );
    }
}