package com.fan.lazyday.application.service.braindispatch;

import com.fan.lazyday.domain.braindispatch.entity.BrainDispatchStatus;
import com.fan.lazyday.domain.braindispatch.po.BrainDispatchLogPO;
import com.fan.lazyday.domain.braindispatch.repository.BrainDispatchLogRepository;
import com.fan.lazyday.infrastructure.utils.id.SnowflakeIdWorker;
import com.fan.lazyday.interfaces.dto.braindispatch.EnvelopeDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrainDispatchServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private BrainDispatchLogRepository repository;

    @Test
    void onAck_codeZero_shouldMarkAcked() throws Exception {
        BrainDispatchService service = service();
        when(repository.findByMsgId("msg-1")).thenReturn(Mono.just(log(BrainDispatchStatus.SENT)));
        when(repository.updateStatus(any(), eq(BrainDispatchStatus.PENDING), eq(BrainDispatchStatus.SENT))).thenReturn(Mono.just(1L));

        StepVerifier.create(service.onAck(ack("msg-1", 0, "ok")))
                .verifyComplete();

        ArgumentCaptor<BrainDispatchLogPO> captor = ArgumentCaptor.forClass(BrainDispatchLogPO.class);
        verify(repository).updateStatus(captor.capture(), eq(BrainDispatchStatus.PENDING), eq(BrainDispatchStatus.SENT));
        assertThat(captor.getValue().getStatus()).isEqualTo("acked");
        assertThat(captor.getValue().getAckedTime()).isNotNull();
    }

    @Test
    void onAck_forPendingRecord_shouldMarkSentThenAcked() throws Exception {
        BrainDispatchService service = service();
        when(repository.findByMsgId("msg-1")).thenReturn(Mono.just(log(BrainDispatchStatus.PENDING)));
        when(repository.updateStatus(any(), eq(BrainDispatchStatus.PENDING), eq(BrainDispatchStatus.SENT))).thenReturn(Mono.just(1L));

        StepVerifier.create(service.onAck(ack("msg-1", 0, "ok")))
                .verifyComplete();

        ArgumentCaptor<BrainDispatchLogPO> captor = ArgumentCaptor.forClass(BrainDispatchLogPO.class);
        verify(repository).updateStatus(captor.capture(), eq(BrainDispatchStatus.PENDING), eq(BrainDispatchStatus.SENT));
        assertThat(captor.getValue().getStatus()).isEqualTo("acked");
        assertThat(captor.getValue().getAckedTime()).isNotNull();
    }

    @Test
    void onAck_nonZeroCode_shouldMarkFailed() throws Exception {
        BrainDispatchService service = service();
        when(repository.findByMsgId("msg-1")).thenReturn(Mono.just(log(BrainDispatchStatus.SENT)));
        when(repository.updateStatus(any(), eq(BrainDispatchStatus.PENDING), eq(BrainDispatchStatus.SENT))).thenReturn(Mono.just(1L));

        StepVerifier.create(service.onAck(ack("msg-1", 1002, "unknown type")))
                .verifyComplete();

        ArgumentCaptor<BrainDispatchLogPO> captor = ArgumentCaptor.forClass(BrainDispatchLogPO.class);
        verify(repository).updateStatus(captor.capture(), eq(BrainDispatchStatus.PENDING), eq(BrainDispatchStatus.SENT));
        assertThat(captor.getValue().getStatus()).isEqualTo("failed");
        assertThat(captor.getValue().getLastError()).isEqualTo("1002:unknown type");
    }

    @Test
    void onAck_unknownMsgId_shouldIgnore() throws Exception {
        BrainDispatchService service = service();
        when(repository.findByMsgId("missing")).thenReturn(Mono.empty());

        StepVerifier.create(service.onAck(ack("missing", 0, "ok")))
                .verifyComplete();

        verify(repository, never()).updateStatus(any(), any());
    }

    private BrainDispatchService service() {
        return new BrainDispatchService(repository, new SnowflakeIdWorker(1, 1));
    }

    private BrainDispatchLogPO log(BrainDispatchStatus status) {
        return new BrainDispatchLogPO()
                .setId(1L)
                .setMsgId("msg-1")
                .setTenantId(7L)
                .setType("CONFIG_UPDATE")
                .setStatus(status.value());
    }

    private EnvelopeDTO ack(String ackedMsgId, int code, String message) throws Exception {
        return new EnvelopeDTO()
                .setType("ACK")
                .setMsgId("ack-" + ackedMsgId)
                .setTenantId(7L)
                .setPayload(OBJECT_MAPPER.readTree("""
                        {"ackedMsgId":"%s","code":%d,"message":"%s"}
                        """.formatted(ackedMsgId, code, message)));
    }
}
