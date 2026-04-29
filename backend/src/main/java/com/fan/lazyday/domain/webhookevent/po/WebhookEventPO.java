package com.fan.lazyday.domain.webhookevent.po;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Getter
@Setter
@Accessors(chain = true)
@Table("t_webhook_event")
public class WebhookEventPO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;
    private Long tenantId;
    private Long configId;
    private String eventType;
    private String payload;
    private String status;
    private Integer retryCount;
    private Instant nextRetryAt;
    private Instant lockedAt;
    private String lockedBy;
    private Integer lastHttpStatus;
    private String lastResponseExcerpt;
    private String lastError;
    private Instant createdTime;
    private Instant deliveredTime;
}
