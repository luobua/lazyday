-- =============================================
-- V3: Quota plans, tenant quotas, call logs
-- =============================================

-- Quota plan templates (Free / Pro / Enterprise)
CREATE TABLE t_quota_plan (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            VARCHAR(50)  NOT NULL,
    qps_limit       INT          NOT NULL DEFAULT 5,
    daily_limit     BIGINT       NOT NULL DEFAULT 1000,
    monthly_limit   BIGINT       NOT NULL DEFAULT 10000,
    max_app_keys    INT          NOT NULL DEFAULT 5,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    create_user     BIGINT,
    create_time     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    update_user     BIGINT,
    update_time     TIMESTAMPTZ
);

COMMENT ON TABLE t_quota_plan IS '配额套餐模板';
COMMENT ON COLUMN t_quota_plan.max_app_keys IS 'AppKey上限, -1表示不限';
COMMENT ON COLUMN t_quota_plan.status IS 'ACTIVE / DISABLED';

-- Tenant quota instance (one per tenant)
CREATE TABLE t_tenant_quota (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id            BIGINT  NOT NULL UNIQUE REFERENCES t_tenant(id),
    plan_id              BIGINT  NOT NULL REFERENCES t_quota_plan(id),
    custom_qps_limit     INT,
    custom_daily_limit   BIGINT,
    custom_monthly_limit BIGINT,
    custom_max_app_keys  INT,
    create_user          BIGINT,
    create_time          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_user          BIGINT,
    update_time          TIMESTAMPTZ
);

CREATE INDEX idx_tenant_quota_tenant ON t_tenant_quota(tenant_id);

COMMENT ON TABLE t_tenant_quota IS '租户配额实例';
COMMENT ON COLUMN t_tenant_quota.custom_qps_limit IS 'Admin自定义覆盖, NULL则使用套餐默认值';

-- Call log partitioned table (partition by month on request_time)
CREATE TABLE t_call_log (
    id              BIGINT       NOT NULL,
    tenant_id       BIGINT       NOT NULL,
    app_key         VARCHAR(64)  NOT NULL,
    path            VARCHAR(500) NOT NULL,
    method          VARCHAR(10)  NOT NULL,
    status_code     SMALLINT     NOT NULL,
    latency_ms      INT          NOT NULL,
    client_ip       VARCHAR(50),
    error_msg       TEXT,
    request_time    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, request_time)
) PARTITION BY RANGE (request_time);

COMMENT ON TABLE t_call_log IS '调用日志(按月分区)';
COMMENT ON COLUMN t_call_log.id IS '雪花ID(应用层生成)';

DO $$
DECLARE
    current_month_start DATE := date_trunc('month', CURRENT_DATE)::DATE;
    next_month_start DATE := (date_trunc('month', CURRENT_DATE) + INTERVAL '1 month')::DATE;
    month_after_next_start DATE := (date_trunc('month', CURRENT_DATE) + INTERVAL '2 month')::DATE;
    current_partition_name TEXT := format('t_call_log_%s', to_char(current_month_start, 'YYYY_MM'));
    next_partition_name TEXT := format('t_call_log_%s', to_char(next_month_start, 'YYYY_MM'));
BEGIN
    EXECUTE format(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF t_call_log FOR VALUES FROM (%L) TO (%L)',
            current_partition_name,
            current_month_start,
            next_month_start
    );

    EXECUTE format(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF t_call_log FOR VALUES FROM (%L) TO (%L)',
            next_partition_name,
            next_month_start,
            month_after_next_start
    );
END $$;

-- Indexes on partitioned table
CREATE INDEX idx_call_log_tenant_time ON t_call_log(tenant_id, request_time);
CREATE INDEX idx_call_log_app_key ON t_call_log(app_key);

-- Seed default plans
INSERT INTO t_quota_plan (name, qps_limit, daily_limit, monthly_limit, max_app_keys, status) VALUES
    ('Free',       5,    1000,    10000,    5,  'ACTIVE'),
    ('Pro',        50,   50000,   500000,  -1,  'ACTIVE'),
    ('Enterprise', 200,  500000,  5000000, -1,  'ACTIVE');

DO $$
DECLARE
    free_plan_id BIGINT;
BEGIN
    SELECT id INTO free_plan_id
    FROM t_quota_plan
    WHERE name = 'Free'
    ORDER BY id
    LIMIT 1;

    INSERT INTO t_tenant_quota (tenant_id, plan_id)
    SELECT tenant.id, free_plan_id
    FROM t_tenant tenant
    WHERE NOT EXISTS (
        SELECT 1
        FROM t_tenant_quota tenant_quota
        WHERE tenant_quota.tenant_id = tenant.id
    );
END $$;
