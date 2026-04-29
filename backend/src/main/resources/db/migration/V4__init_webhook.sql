-- =============================================
-- V4: Webhook configuration and outbound events
-- =============================================

CREATE TABLE t_webhook_config (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id        BIGINT       NOT NULL REFERENCES t_tenant(id),
    name             VARCHAR(100) NOT NULL,
    url              VARCHAR(500) NOT NULL,
    event_types      VARCHAR(500) NOT NULL,
    secret_encrypted VARCHAR(500) NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    create_user      BIGINT,
    create_time      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_user      BIGINT,
    update_time      TIMESTAMPTZ,
    CONSTRAINT chk_webhook_config_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT chk_webhook_config_event_types_not_blank CHECK (length(trim(event_types)) > 0)
);

CREATE INDEX idx_webhook_config_tenant ON t_webhook_config(tenant_id);

COMMENT ON TABLE t_webhook_config IS 'Webhook订阅配置表';
COMMENT ON COLUMN t_webhook_config.event_types IS '订阅事件类型, 逗号分隔';
COMMENT ON COLUMN t_webhook_config.secret_encrypted IS 'AES加密后的HMAC签名密钥';
COMMENT ON COLUMN t_webhook_config.status IS 'ACTIVE / DISABLED';

CREATE TABLE t_webhook_event (
    id                    BIGINT        PRIMARY KEY,
    tenant_id             BIGINT        NOT NULL REFERENCES t_tenant(id),
    config_id             BIGINT        NOT NULL REFERENCES t_webhook_config(id),
    event_type            VARCHAR(50)   NOT NULL,
    payload               JSONB         NOT NULL,
    status                VARCHAR(20)   NOT NULL DEFAULT 'pending',
    retry_count           INT           NOT NULL DEFAULT 0,
    next_retry_at         TIMESTAMPTZ,
    locked_at             TIMESTAMPTZ,
    locked_by             VARCHAR(100),
    last_http_status      INT,
    last_response_excerpt VARCHAR(1024),
    last_error            VARCHAR(500),
    created_time          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    delivered_time        TIMESTAMPTZ,
    CONSTRAINT chk_webhook_event_status CHECK (status IN ('pending', 'delivering', 'succeeded', 'failed', 'permanent_failed')),
    CONSTRAINT chk_webhook_event_retry_count_non_negative CHECK (retry_count >= 0)
);

CREATE INDEX idx_webhook_event_dispatch ON t_webhook_event(status, next_retry_at);
CREATE INDEX idx_webhook_event_tenant_created ON t_webhook_event(tenant_id, created_time DESC);

COMMENT ON TABLE t_webhook_event IS 'Webhook出站事件表';
COMMENT ON COLUMN t_webhook_event.id IS '雪花ID(应用层生成)';
COMMENT ON COLUMN t_webhook_event.payload IS '事件载荷JSONB';
COMMENT ON COLUMN t_webhook_event.status IS 'pending / delivering / succeeded / failed / permanent_failed';
COMMENT ON COLUMN t_webhook_event.locked_by IS '持有投递锁的Backend实例ID';
