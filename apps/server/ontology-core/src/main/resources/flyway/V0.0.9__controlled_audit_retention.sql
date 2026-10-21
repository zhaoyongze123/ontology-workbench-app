-- 审计默认永久保留；只有数据库维护者配置保留期并手动调用维护函数时才清理到期记录。
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'ontology_audit_maintenance'
    ) THEN
        EXECUTE 'CREATE ROLE ontology_audit_maintenance NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE';
    ELSIF EXISTS (
        SELECT 1 FROM pg_catalog.pg_roles
        WHERE rolname = 'ontology_audit_maintenance'
          AND (rolcanlogin OR rolsuper OR rolcreatedb OR rolcreaterole OR rolbypassrls)
    ) THEN
        RAISE EXCEPTION 'ontology_audit_maintenance must remain a restricted NOLOGIN role';
    END IF;
END;
$$;

CREATE TABLE ontology.og_audit_retention_policy (
    policy_key SMALLINT PRIMARY KEY CHECK (policy_key = 1),
    retention_days INTEGER NOT NULL CHECK (retention_days BETWEEN 1 AND 36500),
    updated_at TIMESTAMP NOT NULL DEFAULT LOCALTIMESTAMP
);

COMMENT ON TABLE ontology.og_audit_retention_policy IS
    '仅由数据库维护者设置；未配置策略行时审计记录永久保留且清理函数拒绝执行。';

CREATE OR REPLACE FUNCTION ontology.reject_audit_event_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    -- 仅受控维护函数以专用 NOLOGIN 角色执行到期删除；更新和截断始终禁止。
    IF TG_OP = 'DELETE' AND current_user = 'ontology_audit_maintenance' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'audit events are append-only' USING ERRCODE = '42501';
END;
$$;

CREATE FUNCTION ontology.prune_expired_audit_events()
RETURNS BIGINT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
DECLARE
    configured_days INTEGER;
    cutoff TIMESTAMP;
    deleted_count BIGINT;
BEGIN
    SELECT retention_days INTO configured_days
    FROM ontology.og_audit_retention_policy
    WHERE policy_key = 1;

    IF configured_days IS NULL THEN
        RAISE EXCEPTION 'audit retention policy is not configured' USING ERRCODE = '55000';
    END IF;

    cutoff := LOCALTIMESTAMP - pg_catalog.make_interval(days => configured_days);
    DELETE FROM ontology.og_audit_event WHERE create_time < cutoff;
    GET DIAGNOSTICS deleted_count = ROW_COUNT;

    INSERT INTO ontology.og_audit_event(event_type, actor_user_id, details_json, create_time)
    VALUES ('AUDIT_RETENTION_CLEANUP', NULL,
            pg_catalog.jsonb_build_object('deletedCount', deleted_count,
                                          'retentionDays', configured_days,
                                          'cutoff', cutoff),
            LOCALTIMESTAMP);
    RETURN deleted_count;
END;
$$;

GRANT USAGE, CREATE ON SCHEMA ontology TO ontology_audit_maintenance;
GRANT SELECT ON ontology.og_audit_retention_policy TO ontology_audit_maintenance;
GRANT SELECT, INSERT, DELETE ON ontology.og_audit_event TO ontology_audit_maintenance;
GRANT USAGE, SELECT ON SEQUENCE ontology.og_audit_event_id_seq TO ontology_audit_maintenance;
ALTER FUNCTION ontology.prune_expired_audit_events() OWNER TO ontology_audit_maintenance;
REVOKE CREATE ON SCHEMA ontology FROM ontology_audit_maintenance;
REVOKE ALL ON FUNCTION ontology.prune_expired_audit_events() FROM PUBLIC;
REVOKE ALL ON TABLE ontology.og_audit_retention_policy FROM PUBLIC;

COMMENT ON FUNCTION ontology.prune_expired_audit_events() IS
    '由数据库维护者按已配置保留期手动执行；不配置策略时拒绝清理，不创建自动清理任务。';
