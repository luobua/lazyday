-- =============================================
-- V5: Brain dispatch log
-- =============================================

CREATE TABLE t_brain_dispatch_log (
    id           BIGINT       PRIMARY KEY,
    msg_id       VARCHAR(64)  NOT NULL,
    tenant_id    BIGINT       NOT NULL,
    type         VARCHAR(32)  NOT NULL,
    payload      JSONB        NOT NULL,
    status       VARCHAR(16)  NOT NULL,
    last_error   VARCHAR(500),
    create_user  UUID,
    create_time  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_user  UUID,
    update_time  TIMESTAMPTZ,
    acked_time   TIMESTAMPTZ,
    CONSTRAINT uk_brain_dispatch_log_msg_id UNIQUE (msg_id),
    CONSTRAINT chk_brain_dispatch_log_type CHECK (type IN ('CONFIG_UPDATE', 'ACK', 'HEARTBEAT')),
    CONSTRAINT chk_brain_dispatch_log_status CHECK (status IN ('pending', 'sent', 'acked', 'failed', 'timeout'))
);

CREATE INDEX idx_brain_dispatch_log_tenant_status_created
    ON t_brain_dispatch_log(tenant_id, status, create_time DESC);

COMMENT ON TABLE t_brain_dispatch_log IS 'Backend到Edge下发日志表';
COMMENT ON COLUMN t_brain_dispatch_log.id IS '雪花ID(应用层生成)';
COMMENT ON COLUMN t_brain_dispatch_log.msg_id IS '下发消息唯一ID';
COMMENT ON COLUMN t_brain_dispatch_log.payload IS '下发载荷JSONB';
COMMENT ON COLUMN t_brain_dispatch_log.status IS 'pending / sent / acked / failed / timeout';
