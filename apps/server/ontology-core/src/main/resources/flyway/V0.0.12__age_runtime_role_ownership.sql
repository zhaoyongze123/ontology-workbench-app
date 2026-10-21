-- AGE 在写入 Cypher 时会按当前角色动态创建标签表；会话必须以 NOLOGIN 运行角色为有效身份。
DO $$
DECLARE
    graph_schema RECORD;
    graph_object RECORD;
    login_role RECORD;
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_roles
        WHERE rolname = 'ontology_app_runtime' AND NOT rolcanlogin
          AND NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole
          AND NOT rolbypassrls AND NOT rolreplication
    ) THEN
        RAISE EXCEPTION 'ontology_app_runtime must be a restricted NOLOGIN role';
    END IF;

    IF NOT pg_catalog.pg_has_role(current_user, 'ontology_app_runtime', 'MEMBER') THEN
        RAISE EXCEPTION 'migration role must temporarily be a member of ontology_app_runtime';
    END IF;

    FOR graph_schema IN
        SELECT nspname FROM pg_catalog.pg_namespace
        WHERE nspname ~ '^(ontology|entity)[1-9][0-9]*$'
    LOOP
        EXECUTE pg_catalog.format('REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA %I FROM PUBLIC',
                                  graph_schema.nspname);
        EXECUTE pg_catalog.format('REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA %I FROM PUBLIC',
                                  graph_schema.nspname);
        EXECUTE pg_catalog.format('REVOKE ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA %I FROM PUBLIC',
                                  graph_schema.nspname);

        -- 清理旧版本运行登录名的直接 ACL；登录凭据只负责认证，不直接持有对象权限。
        FOR login_role IN
            SELECT r.rolname
            FROM pg_catalog.pg_roles r
            WHERE r.rolcanlogin
              AND pg_catalog.pg_has_role(r.oid, 'ontology_app_runtime', 'MEMBER')
        LOOP
            EXECUTE pg_catalog.format('REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA %I FROM %I',
                                      graph_schema.nspname, login_role.rolname);
            EXECUTE pg_catalog.format('REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA %I FROM %I',
                                      graph_schema.nspname, login_role.rolname);
            EXECUTE pg_catalog.format('REVOKE ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA %I FROM %I',
                                      graph_schema.nspname, login_role.rolname);
        END LOOP;

        EXECUTE pg_catalog.format('GRANT USAGE, CREATE ON SCHEMA %I TO ontology_app_runtime',
                                  graph_schema.nspname);

        -- 已有 AGE 标签对象统一归 NOLOGIN 运行组；数据库登录账号不再直接成为对象 owner。
        FOR graph_object IN
            SELECT c.relname, c.relkind
            FROM pg_catalog.pg_class c
            JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = graph_schema.nspname AND c.relkind IN ('r', 'p', 'S')
              -- 表 owner 变更会自动转移其自增序列。
              AND (c.relkind <> 'S' OR NOT EXISTS (
                  SELECT 1 FROM pg_catalog.pg_depend d
                  WHERE d.classid = 'pg_class'::regclass AND d.objid = c.oid
                    AND d.refclassid = 'pg_class'::regclass AND d.deptype IN ('a', 'i')))
        LOOP
            IF graph_object.relkind = 'S' THEN
                EXECUTE pg_catalog.format('ALTER SEQUENCE %I.%I OWNER TO ontology_app_runtime',
                                          graph_schema.nspname, graph_object.relname);
            ELSE
                EXECUTE pg_catalog.format('ALTER TABLE %I.%I OWNER TO ontology_app_runtime',
                                          graph_schema.nspname, graph_object.relname);
            END IF;
        END LOOP;

        EXECUTE pg_catalog.format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA %I '
                                  || 'TO ontology_app_runtime', graph_schema.nspname);
        EXECUTE pg_catalog.format('GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA %I '
                                  || 'TO ontology_app_runtime', graph_schema.nspname);

        -- Java 连接以 ontology_app_runtime 为有效角色；其新建函数不能默认向 PUBLIC 暴露。
        EXECUTE pg_catalog.format('ALTER DEFAULT PRIVILEGES FOR ROLE ontology_app_runtime IN SCHEMA %I '
                                  || 'REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC', graph_schema.nspname);
    END LOOP;
END;
$$;

ALTER DEFAULT PRIVILEGES FOR ROLE ontology_app_runtime
    REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
