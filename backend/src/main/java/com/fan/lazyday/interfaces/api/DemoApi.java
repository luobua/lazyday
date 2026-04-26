package com.fan.lazyday.interfaces.api;

import com.fan.lazyday.interfaces.request.DemoRequest;
import com.fan.lazyday.interfaces.response.DemoResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import reactor.core.publisher.Mono;

public interface DemoApi {

    @GetMapping(value = "demo")
    Mono<DemoResponse> demo(@RequestBody @Validated Mono<DemoRequest> request);
}
