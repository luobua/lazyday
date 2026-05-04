package com.fan.lazyday.interfaces.handler.internal;

import com.fan.lazyday.application.service.braindispatch.BrainDispatchService;
import com.fan.lazyday.application.service.braindispatch.BrainDispatchQueryService;
import com.fan.lazyday.infrastructure.ws.EdgeConnectionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalDispatchHandlerTest {

    @Test
    void dispatch_withoutEdgeConnection_shouldStillReturnMsgId() throws Exception {
        BrainDispatchService service = mock(BrainDispatchService.class);
        BrainDispatchQueryService queryService = mock(BrainDispatchQueryService.class);
        EdgeConnectionRegistry registry = mock(EdgeConnectionRegistry.class);
        InternalDispatchHandler handler = new InternalDispatchHandler(service, queryService, registry);
        ObjectMapper objectMapper = new ObjectMapper();
        var payload = objectMapper.readTree("{\"hello\":\"edge\"}");

        when(service.createPending(7L, payload)).thenReturn(Mono.just("1888"));
        when(registry.sessionCount()).thenReturn(0);

        StepVerifier.create(handler.dispatch(7L, Mono.just("{\"hello\":\"edge\"}")))
                .assertNext(response -> assertThat(response.getData()).containsEntry("msgId", "1888"))
                .verifyComplete();

        verify(registry, never()).broadcast(org.mockito.ArgumentMatchers.any());
    }
}
