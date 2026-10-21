-- 运行账号只获得业务表权限；迁移账号拥有 ontology schema，AGE 图建删通过受限函数代理。
DO $$
DECLARE
    runtime_role RECORD;
    graph_role RECORD;
    migrator_role RECORD;
BEGIN
    SELECT rolcanlogin, rolsuper, rolcreatedb, rolcreaterole, rolbypassrls, rolreplication
      INTO runtime_role FROM pg_catalog.pg_roles WHERE rolname = 'ontology_app_runtime';
    IF NOT FOUND OR runtime_role.rolcanlogin OR runtime_role.rolsuper
       OR runtime_role.rolcreatedb OR runtime_role.rolcreaterole
       OR runtime_role.rolbypassrls OR runtime_role.rolreplication THEN
        RAISE EXCEPTION 'ontology_app_runtime must be a restricted NOLOGIN role';
    END IF;

    SELECT rolcanlogin, rolsuper, rolcreatedb, rolcreaterole, rolbypassrls, rolreplication
      INTO graph_role FROM pg_catalog.pg_roles WHERE rolname = 'ontology_age_graph_maintainer';
    IF NOT FOUND OR graph_role.rolcanlogin OR graph_role.rolsuper
       OR graph_role.rolcreatedb OR graph_role.rolcreaterole
       OR graph_role.rolbypassrls OR graph_role.rolreplication THEN
        RAISE EXCEPTION 'ontology_age_graph_maintainer must be a restricted NOLOGIN role';
    END IF;

    SELECT rolsuper, rolcreatedb, rolcreaterole, rolbypassrls, rolreplication
      INTO migrator_role FROM pg_catalog.pg_roles WHERE rolname = current_user;
    IF NOT FOUND OR migrator_role.rolsuper OR migrator_role.rolcreatedb
       OR migrator_role.rolcreaterole OR migrator_role.rolbypassrls
       OR migrator_role.rolreplication THEN
        RAISE EXCEPTION 'database migrations must run as a non-privileged migration role';
    END IF;

    IF NOT pg_catalog.pg_has_role(current_user, 'ontology_age_graph_maintainer', 'MEMBER') THEN
        RAISE EXCEPTION 'migration role must be allowed to transfer graph wrapper ownership';
    END IF;
END;
$$;

-- 既有表只授予业务 CRUD；不给运行账号清表、建外键或安装触发器的能力。
GRANT USAGE ON SCHEMA ontology TO ontology_app_runtime;
REVOKE CREATE ON SCHEMA ontology FROM PUBLIC, ontology_app_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA ontology TO ontology_app_runtime;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA ontology TO ontology_app_runtime;
REVOKE TRUNCATE, REFERENCES, TRIGGER ON ALL TABLES IN SCHEMA ontology FROM ontology_app_runtime;
REVOKE UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER
    ON TABLE ontology.og_audit_event FROM ontology_app_runtime;
GRANT SELECT, INSERT ON TABLE ontology.og_audit_event TO ontology_app_runtime;
REVOKE ALL ON TABLE ontology.og_audit_retention_policy FROM ontology_app_runtime;
REVOKE ALL ON TABLE ontology.og_bootstrap_run FROM ontology_app_runtime;

-- Flyway metadata is owned by the migration role and must not be visible to the app runtime.
DO $$
BEGIN
    IF pg_catalog.to_regclass('ontology.flyway_schema_history') IS NOT NULL THEN
        REVOKE ALL ON TABLE ontology.flyway_schema_history FROM ontology_app_runtime;
    END IF;
END;
$$;

-- 新业务表和自增序列默认获得业务运行权限；管理对象放在受限 schema，函数执行权必须逐个授予。
ALTER DEFAULT PRIVILEGES IN SCHEMA ontology
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ontology_app_runtime;
ALTER DEFAULT PRIVILEGES IN SCHEMA ontology
    GRANT USAGE, SELECT ON SEQUENCES TO ontology_app_runtime;
ALTER DEFAULT PRIVILEGES
    REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

CREATE FUNCTION ontology.create_age_graph(p_graph_name TEXT)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
BEGIN
    -- AGE 的图库名只能由 Ontology 图类型前缀和正数业务 ID 组成。
    IF p_graph_name IS NULL OR p_graph_name !~ '^(ontology|entity)[1-9][0-9]*$' THEN
        RAISE EXCEPTION 'invalid Ontology AGE graph name' USING ERRCODE = '22023';
    END IF;

    PERFORM ag_catalog.create_graph(p_graph_name::NAME);
    EXECUTE pg_catalog.format('GRANT USAGE, CREATE ON SCHEMA %I TO ontology_app_runtime', p_graph_name);
    -- AGE 在 create_graph 时先建目录序列；先由当前 owner 授权，再把目录表/序列 owner 转给运行组。
    EXECUTE pg_catalog.format('GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA %I TO ontology_app_runtime',
                              p_graph_name);
    -- AGE 写标签时要求应用角色拥有图目录表；保持数据库级 CREATE 仍只归 helper。
    EXECUTE pg_catalog.format('ALTER TABLE %I._ag_label_vertex OWNER TO ontology_app_runtime', p_graph_name);
    EXECUTE pg_catalog.format('ALTER TABLE %I._ag_label_edge OWNER TO ontology_app_runtime', p_graph_name);
    EXECUTE pg_catalog.format(
        'ALTER DEFAULT PRIVILEGES FOR ROLE ontology_age_graph_maintainer IN SCHEMA %I '
        || 'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ontology_app_runtime',
        p_graph_name);
    EXECUTE pg_catalog.format(
        'ALTER DEFAULT PRIVILEGES FOR ROLE ontology_age_graph_maintainer IN SCHEMA %I '
        || 'GRANT USAGE, SELECT ON SEQUENCES TO ontology_app_runtime',
        p_graph_name);
END;
$$;

CREATE FUNCTION ontology.drop_age_graph(p_graph_name TEXT)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog
AS $$
BEGIN
    -- 只允许删除 Ontology 自己命名空间中的图库，不能把函数用作任意 schema 删除器。
    IF p_graph_name IS NULL OR p_graph_name !~ '^(ontology|entity)[1-9][0-9]*$' THEN
        RAISE EXCEPTION 'invalid Ontology AGE graph name' USING ERRCODE = '22023';
    END IF;

    PERFORM ag_catalog.drop_graph(p_graph_name::NAME, TRUE);
END;
$$;

COMMENT ON FUNCTION ontology.create_age_graph(TEXT) IS
    '受限创建 Ontology AGE 图；运行账号不能直接创建数据库 schema。';
COMMENT ON FUNCTION ontology.drop_age_graph(TEXT) IS
    '受限删除 Ontology AGE 图；只接受 ontology/entity 加正数 ID。';

-- AGE 图维护角色是唯一需要数据库 CREATE 权限的对象，且它不能登录。
GRANT CREATE ON SCHEMA ontology TO ontology_age_graph_maintainer;
GRANT EXECUTE ON FUNCTION ontology.create_age_graph(TEXT) TO ontology_app_runtime;
GRANT EXECUTE ON FUNCTION ontology.drop_age_graph(TEXT) TO ontology_app_runtime;
-- 先授予执行权再转交 owner，避免迁移账号转交后失去修改该函数 ACL 的能力。
ALTER FUNCTION ontology.create_age_graph(TEXT) OWNER TO ontology_age_graph_maintainer;
ALTER FUNCTION ontology.drop_age_graph(TEXT) OWNER TO ontology_age_graph_maintainer;
REVOKE CREATE ON SCHEMA ontology FROM ontology_age_graph_maintainer;

REVOKE EXECUTE ON FUNCTION ontology.reject_audit_event_mutation() FROM PUBLIC, ontology_app_runtime;

COMMENT ON SCHEMA ontology IS
    'Ontology application data. Runtime table grants follow the allowlist tested by DatabasePrivilegesIntegrationTest.';
