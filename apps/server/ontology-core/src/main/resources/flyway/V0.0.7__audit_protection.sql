-- 安全审计只允许追加；保留期限与清理流程由受控数据库维护策略另行配置。
-- 审计保留历史操作者编号，避免删除/停用用户时外键级联改写既有审计记录。
ALTER TABLE ontology.og_audit_event
    DROP CONSTRAINT IF EXISTS og_audit_event_actor_user_id_fkey;

CREATE OR REPLACE FUNCTION ontology.reject_audit_event_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit events are append-only' USING ERRCODE = '42501';
END;
$$;

CREATE TRIGGER trg_og_audit_event_no_update_delete
    BEFORE UPDATE OR DELETE ON ontology.og_audit_event
    FOR EACH ROW EXECUTE FUNCTION ontology.reject_audit_event_mutation();

CREATE TRIGGER trg_og_audit_event_no_truncate
    BEFORE TRUNCATE ON ontology.og_audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION ontology.reject_audit_event_mutation();

CREATE INDEX idx_og_audit_event_actor_created
    ON ontology.og_audit_event(actor_user_id, create_time DESC);
