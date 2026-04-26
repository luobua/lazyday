package com.fan.lazyday.interfaces.handler;

import com.fan.lazyday.application.facade.DemoFacade;
import com.fan.lazyday.application.service.DemoService;
import com.fan.lazyday.infrastructure.config.path.RequestMappingApiV1;
import com.fan.lazyday.interfaces.api.DemoApi;
import com.fan.lazyday.interfaces.request.DemoRequest;
import com.fan.lazyday.interfaces.response.DemoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMappingApiV1
@RequiredArgsConstructor
public class DemoHandler implements DemoApi {
    private final DemoService demoService;
    private final DemoFacade demoFacade;


    @Override
    public Mono<DemoResponse> demo(Mono<DemoRequest> request) {
        return Mono.fromSupplier(DemoResponse::new);
    }
}
