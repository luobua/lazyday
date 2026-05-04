-- =============================================
-- V5: Brain dispatch log
-- =============================================

CREATE TABLE t_brain_dispatch_log (
    id           BIGINT       NOT NULL PRIMARY KEY,
    msg_id       VARCHAR(64)  NOT NULL,
    tenant_id    BIGINT       NOT NULL,
    type         VARCHAR(32)  NOT NULL,
    payload      JSON         NOT NULL,
    status       VARCHAR(16)  NOT NULL,
    last_error   VARCHAR(500),
    create_user  CHAR(36),
    create_time  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_user  CHAR(36),
    update_time  TIMESTAMP(6) NULL,
    acked_time   TIMESTAMP(6) NULL,
    CONSTRAINT uk_brain_dispatch_log_msg_id UNIQUE (msg_id),
    CONSTRAINT chk_brain_dispatch_log_type CHECK (type IN ('CONFIG_UPDATE', 'ACK', 'HEARTBEAT')),
    CONSTRAINT chk_brain_dispatch_log_status CHECK (status IN ('pending', 'sent', 'acked', 'failed', 'timeout')),
    INDEX idx_brain_dispatch_log_tenant_status_created (tenant_id, status, create_time DESC)
);
