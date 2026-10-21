-- Bootstrap 只使用专用维护组；业务运行角色不得读取或修改初始化 ledger。
DO $$
DECLARE
    bootstrap_role RECORD;
BEGIN
    SELECT rolcanlogin, rolsuper, rolcreatedb, rolcreaterole, rolbypassrls, rolreplication
      INTO bootstrap_role
      FROM pg_catalog.pg_roles
     WHERE rolname = 'ontology_bootstrap_maintenance';
    IF NOT FOUND OR bootstrap_role.rolcanlogin OR bootstrap_role.rolsuper
       OR bootstrap_role.rolcreatedb OR bootstrap_role.rolcreaterole
       OR bootstrap_role.rolbypassrls OR bootstrap_role.rolreplication THEN
        RAISE EXCEPTION 'ontology_bootstrap_maintenance must be a restricted NOLOGIN role';
    END IF;
END;
$$;

GRANT USAGE ON SCHEMA ontology TO ontology_bootstrap_maintenance;

-- 服务只需读取既有身份/项目以检测冲突，并插入本次受控种子；不授予更新、删除或清表权限。
GRANT SELECT (seed_key, input_digest, details_json), INSERT (seed_key, input_digest, completed_at, details_json)
    ON TABLE ontology.og_bootstrap_run TO ontology_bootstrap_maintenance;
GRANT SELECT (id, username), INSERT (id, username, display_name, system_admin, enabled, auth_version)
    ON TABLE ontology.og_user TO ontology_bootstrap_maintenance;
GRANT SELECT (user_id), INSERT (user_id, password_hash)
    ON TABLE ontology.og_password_credential TO ontology_bootstrap_maintenance;
GRANT SELECT (id, bootstrap_key), INSERT (id, name, description, bootstrap_key)
    ON TABLE ontology.og_organization TO ontology_bootstrap_maintenance;
GRANT SELECT (project_id, role), INSERT (project_id, user_id, role)
    ON TABLE ontology.og_project_member TO ontology_bootstrap_maintenance;
GRANT INSERT (event_type, actor_user_id, details_json)
    ON TABLE ontology.og_audit_event TO ontology_bootstrap_maintenance;
GRANT USAGE, SELECT ON SEQUENCE ontology.og_audit_event_id_seq TO ontology_bootstrap_maintenance;

REVOKE CREATE ON SCHEMA ontology FROM ontology_bootstrap_maintenance;
