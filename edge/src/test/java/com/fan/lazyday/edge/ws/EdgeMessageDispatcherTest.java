package com.fan.lazyday.edge.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class EdgeMessageDispatcherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void envelope_shouldRoundTrip() throws Exception {
        EnvelopeDTO envelope = configUpdate("msg-1");

        String json = objectMapper.writeValueAsString(envelope);
        EnvelopeDTO parsed = objectMapper.readValue(json, EnvelopeDTO.class);

        assertThat(parsed.getType()).isEqualTo("CONFIG_UPDATE");
        assertThat(parsed.getPayload().get("hello").asText()).isEqualTo("edge");
    }

    @Test
    void configUpdate_shouldAckCodeZero() {
        EdgeMessageDispatcher dispatcher = new EdgeMessageDispatcher(objectMapper, new MessageIdLru(1024));
        Sinks.Many<EnvelopeDTO> outbound = Sinks.many().unicast().onBackpressureBuffer();

        StepVerifier.create(dispatcher.dispatch(configUpdate("msg-1"), outbound))
                .verifyComplete();

        StepVerifier.create(outbound.asFlux().take(1))
                .assertNext(ack -> {
                    assertThat(ack.getType()).isEqualTo("ACK");
                    assertThat(ack.getPayload().get("ackedMsgId").asText()).isEqualTo("msg-1");
                    assertThat(ack.getPayload().get("code").asInt()).isZero();
                })
                .verifyComplete();
    }

    @Test
    void duplicateMsgId_shouldAckButNotGrowLru() {
        MessageIdLru lru = new MessageIdLru(1024);
        EdgeMessageDispatcher dispatcher = new EdgeMessageDispatcher(objectMapper, lru);
        Sinks.Many<EnvelopeDTO> outbound = Sinks.many().unicast().onBackpressureBuffer();

        StepVerifier.create(dispatcher.dispatch(configUpdate("msg-1"), outbound)
                        .then(dispatcher.dispatch(configUpdate("msg-1"), outbound)))
                .verifyComplete();

        assertThat(lru.size()).isEqualTo(1);
        StepVerifier.create(outbound.asFlux().take(2))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void unknownType_shouldAckCode1002() {
        EdgeMessageDispatcher dispatcher = new EdgeMessageDispatcher(objectMapper, new MessageIdLru(1024));
        Sinks.Many<EnvelopeDTO> outbound = Sinks.many().unicast().onBackpressureBuffer();

        StepVerifier.create(dispatcher.dispatch(new EnvelopeDTO()
                                .setType("OTHER")
                                .setMsgId("msg-1")
                                .setTenantId(7L), outbound))
                .verifyComplete();

        StepVerifier.create(outbound.asFlux().take(1))
                .assertNext(ack -> assertThat(ack.getPayload().get("code").asInt()).isEqualTo(1002))
                .verifyComplete();
    }

    private EnvelopeDTO configUpdate(String msgId) {
        return new EnvelopeDTO()
                .setType("CONFIG_UPDATE")
                .setMsgId(msgId)
                .setTenantId(7L)
                .setPayload(objectMapper.createObjectNode().put("hello", "edge"));
    }
}
