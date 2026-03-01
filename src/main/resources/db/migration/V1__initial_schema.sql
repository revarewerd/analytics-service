-- ============================================================
-- Analytics Service: V1 — Начальная схема
-- PostgreSQL: шаблоны, расписания, история
-- TimescaleDB: continuous aggregates (создаются в timescaledb-init.sql)
-- ============================================================

-- Шаблоны отчётов
CREATE TABLE IF NOT EXISTS report_templates (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    organization_id BIGINT NOT NULL,
    report_type     VARCHAR(50) NOT NULL,
    config          JSONB NOT NULL DEFAULT '{}',
    default_filters JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      BIGINT
);

CREATE INDEX idx_report_templates_org ON report_templates(organization_id);
CREATE INDEX idx_report_templates_type ON report_templates(report_type);

-- Расписания отчётов
CREATE TABLE IF NOT EXISTS scheduled_reports (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    organization_id BIGINT NOT NULL,
    template_id     BIGINT REFERENCES report_templates(id),
    schedule        VARCHAR(100) NOT NULL,     -- Cron-выражение
    timezone        VARCHAR(50) NOT NULL DEFAULT 'UTC',
    report_type     VARCHAR(50) NOT NULL,
    vehicle_ids     BIGINT[] NOT NULL DEFAULT '{}',
    group_ids       BIGINT[] NOT NULL DEFAULT '{}',
    period_type     VARCHAR(20) NOT NULL DEFAULT 'day',
    delivery_channels VARCHAR(20)[] NOT NULL DEFAULT '{}',
    recipients      JSONB NOT NULL DEFAULT '{}',
    export_format   VARCHAR(10) NOT NULL DEFAULT 'xlsx',
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    last_run_at     TIMESTAMPTZ,
    next_run_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_scheduled_reports_org ON scheduled_reports(organization_id);
CREATE INDEX idx_scheduled_reports_next_run ON scheduled_reports(next_run_at) WHERE enabled = TRUE;
CREATE INDEX idx_scheduled_reports_enabled ON scheduled_reports(enabled);

-- История отчётов
CREATE TABLE IF NOT EXISTS report_history (
    id              BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    user_id         BIGINT,
    scheduled_id    BIGINT REFERENCES scheduled_reports(id),
    report_type     VARCHAR(50) NOT NULL,
    parameters      JSONB NOT NULL DEFAULT '{}',
    status          VARCHAR(20) NOT NULL DEFAULT 'pending',
    file_url        VARCHAR(1024),
    file_size       BIGINT,
    error_message   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ
);

CREATE INDEX idx_report_history_org ON report_history(organization_id);
CREATE INDEX idx_report_history_status ON report_history(status);
CREATE INDEX idx_report_history_created ON report_history(created_at DESC);

-- ============================================================
-- TimescaleDB Continuous Aggregates
-- (запускать на tracker_gps базе, не на tracker_analytics!)
-- Эти запросы вынесены сюда для справки, фактическое создание
-- в infra/databases/timescaledb-init.sql
-- ============================================================

-- Суточная статистика по ТС
-- CREATE MATERIALIZED VIEW daily_vehicle_stats
-- WITH (timescaledb.continuous) AS
-- SELECT
--     time_bucket('1 day', timestamp) AS day,
--     device_id,
--     SUM(mileage) AS mileage_meters,
--     AVG(speed) AS avg_speed,
--     MAX(speed) AS max_speed,
--     COUNT(*) AS point_count,
--     MIN(timestamp) AS first_point,
--     MAX(timestamp) AS last_point
-- FROM gps_positions
-- GROUP BY day, device_id;

-- Почасовая статистика топлива
-- CREATE MATERIALIZED VIEW hourly_fuel_stats
-- WITH (timescaledb.continuous) AS
-- SELECT
--     time_bucket('1 hour', timestamp) AS hour,
--     device_id,
--     FIRST(fuel_level, timestamp) AS start_fuel_level,
--     LAST(fuel_level, timestamp) AS end_fuel_level,
--     MIN(fuel_level) AS min_fuel_level,
--     MAX(fuel_level) AS max_fuel_level,
--     SUM(mileage) AS mileage_meters
-- FROM gps_positions
-- WHERE fuel_level IS NOT NULL
-- GROUP BY hour, device_id;

-- Суточная статистика движения
-- CREATE MATERIALIZED VIEW daily_motion_stats
-- WITH (timescaledb.continuous) AS
-- SELECT
--     time_bucket('1 day', timestamp) AS day,
--     device_id,
--     SUM(CASE WHEN speed > 3 THEN 1 ELSE 0 END) AS moving_seconds,
--     SUM(CASE WHEN speed <= 3 AND ignition = TRUE THEN 1 ELSE 0 END) AS idle_seconds,
--     SUM(CASE WHEN ignition = TRUE THEN 1 ELSE 0 END) AS engine_seconds
-- FROM gps_positions
-- GROUP BY day, device_id;
