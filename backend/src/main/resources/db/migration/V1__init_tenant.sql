-- =============================================
-- V1: Tenant, User extension, AppKey tables
-- =============================================

-- Tenant table
CREATE TABLE t_tenant (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    plan_type   VARCHAR(32)  NOT NULL DEFAULT 'FREE',
    contact_email VARCHAR(128),
    create_user BIGINT,
    create_time TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_user BIGINT,
    update_time TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE t_tenant IS '租户表';
COMMENT ON COLUMN t_tenant.status IS '状态: ACTIVE, SUSPENDED';
COMMENT ON COLUMN t_tenant.plan_type IS '套餐类型: FREE, BASIC, PRO, ENTERPRISE';

-- User table (includes original fields + phase-1 extensions)
CREATE TABLE t_user (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            VARCHAR(64),
    version         BIGINT,
    password_hash   VARCHAR(128),
    role            VARCHAR(32)  NOT NULL DEFAULT 'TENANT_ADMIN',
    tenant_id       BIGINT       REFERENCES t_tenant(id),
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    email           VARCHAR(128),
    username        VARCHAR(64),
    create_user     BIGINT,
    create_time     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_user     BIGINT,
    update_time     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_username ON t_user(username) WHERE username IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_email ON t_user(email) WHERE email IS NOT NULL;

COMMENT ON TABLE t_user IS '用户表';
COMMENT ON COLUMN t_user.role IS '角色: TENANT_ADMIN, PLATFORM_ADMIN';
COMMENT ON COLUMN t_user.status IS '状态: ACTIVE, DISABLED';

-- AppKey table
CREATE TABLE t_app_key (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id            BIGINT       NOT NULL REFERENCES t_tenant(id),
    name                 VARCHAR(64)  NOT NULL,
    app_key              VARCHAR(64)  NOT NULL,
    secret_key_encrypted VARCHAR(512) NOT NULL,
    secret_key_old       VARCHAR(512),
    rotated_at           TIMESTAMPTZ,
    grace_period_end     TIMESTAMPTZ,
    status               VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    scopes               VARCHAR(512),
    create_user          BIGINT,
    create_time          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_user          BIGINT,
    update_time          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_app_key ON t_app_key(app_key);
CREATE INDEX idx_app_key_tenant ON t_app_key(tenant_id);

COMMENT ON TABLE t_app_key IS 'API凭证表';
COMMENT ON COLUMN t_app_key.app_key IS '公开标识: ak_ 前缀';
COMMENT ON COLUMN t_app_key.secret_key_encrypted IS 'AES加密后的SecretKey';
COMMENT ON COLUMN t_app_key.secret_key_old IS '轮换宽限期内的旧密钥(加密)';
COMMENT ON COLUMN t_app_key.status IS '状态: ACTIVE, DISABLED';
COMMENT ON COLUMN t_app_key.scopes IS '权限范围, 逗号分隔: ai:chat,ai:tts,ai:asr';