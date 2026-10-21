\set ON_ERROR_STOP on
\getenv runtime_login ONTOLOGY_DB_USER

-- 在受控 Flyway 迁移完成后由数据库管理员执行，关闭旧直接授权并验证最终边界。
SELECT pg_catalog.format('ALTER ROLE %I WITH LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS NOREPLICATION',
                         :'runtime_login')
\gexec
SELECT pg_catalog.format('GRANT ontology_app_runtime TO %I WITH INHERIT FALSE, SET TRUE', :'runtime_login')
\gexec
-- 登录账号在目标数据库会话自动切换到 NOLOGIN 权限组；漏配时业务权限会默认拒绝。
SELECT pg_catalog.format('ALTER ROLE %I IN DATABASE %I SET role TO %L',
                         :'runtime_login', pg_catalog.current_database(), 'ontology_app_runtime')
\gexec
SELECT pg_catalog.format('REVOKE CREATE, TEMP ON DATABASE %I FROM PUBLIC, ontology_app_runtime, ontology_migrator, %I',
                         pg_catalog.current_database(), :'runtime_login')
\gexec
SELECT pg_catalog.format('GRANT CONNECT ON DATABASE %I TO ontology_migrator, %I',
                         pg_catalog.current_database(), :'runtime_login')
\gexec
SELECT pg_catalog.format('GRANT CREATE ON DATABASE %I TO ontology_age_graph_maintainer',
                         pg_catalog.current_database())
\gexec

-- 迁移结束后移除临时 owner 成员关系；以后每次迁移由 prepare 脚本按需临时授予。
REVOKE ontology_app_runtime, ontology_age_graph_maintainer, ontology_audit_maintenance FROM ontology_migrator;

REVOKE ALL PRIVILEGES ON SCHEMA ontology FROM PUBLIC;
SELECT pg_catalog.format('REVOKE ALL PRIVILEGES ON SCHEMA ontology FROM %I', :'runtime_login')
\gexec
GRANT USAGE ON SCHEMA ontology TO ontology_app_runtime;
REVOKE CREATE ON SCHEMA ontology FROM ontology_app_runtime;
GRANT USAGE ON SCHEMA ag_catalog TO ontology_app_runtime, ontology_age_graph_maintainer;
GRANT USAGE ON TYPE ag_catalog.agtype TO ontology_app_runtime;

-- 登录角色不直接持有对象权限；所有业务权限统一通过 NOLOGIN 运行角色组授予。
SELECT pg_catalog.format('REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA ontology FROM %I', :'runtime_login')
\gexec
SELECT pg_catalog.format('REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA ontology FROM %I', :'runtime_login')
\gexec
SELECT pg_catalog.format('REVOKE ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA ontology FROM %I', :'runtime_login')
\gexec
REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA ontology FROM PUBLIC;
REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA ontology FROM PUBLIC;
REVOKE ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA ontology FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE ontology_app_runtime REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA ontology TO ontology_app_runtime;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA ontology TO ontology_app_runtime;
REVOKE TRUNCATE, REFERENCES, TRIGGER ON ALL TABLES IN SCHEMA ontology FROM ontology_app_runtime;
REVOKE UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER
  ON TABLE ontology.og_audit_event FROM ontology_app_runtime;
GRANT SELECT, INSERT ON TABLE ontology.og_audit_event TO ontology_app_runtime;
REVOKE ALL ON TABLE ontology.og_audit_retention_policy FROM ontology_app_runtime;
REVOKE ALL ON TABLE ontology.og_bootstrap_run FROM ontology_app_runtime;
DO $$
BEGIN
    IF pg_catalog.to_regclass('ontology.flyway_schema_history') IS NOT NULL THEN
        REVOKE ALL ON TABLE ontology.flyway_schema_history FROM ontology_app_runtime;
    END IF;
END;
$$;

-- AGE 的底层任意建删函数只给不可登录图维护角色。
REVOKE EXECUTE ON FUNCTION ag_catalog.create_graph(name)
  FROM PUBLIC, ontology_app_runtime;
REVOKE EXECUTE ON FUNCTION ag_catalog.drop_graph(name, boolean)
  FROM PUBLIC, ontology_app_runtime;
SELECT pg_catalog.format('REVOKE EXECUTE ON FUNCTION ag_catalog.create_graph(name) FROM %I', :'runtime_login')
\gexec
SELECT pg_catalog.format('REVOKE EXECUTE ON FUNCTION ag_catalog.drop_graph(name, boolean) FROM %I', :'runtime_login')
\gexec
GRANT EXECUTE ON FUNCTION ag_catalog.create_graph(name) TO ontology_age_graph_maintainer;
GRANT EXECUTE ON FUNCTION ag_catalog.drop_graph(name, boolean) TO ontology_age_graph_maintainer;

-- 新旧 AGE schema 都归维护角色所有；Java 只在具体图 schema 中创建/操作标签表。
DO $$
DECLARE
    graph_schema RECORD;
    object_record RECORD;
BEGIN
    FOR graph_schema IN
        SELECT nspname FROM pg_catalog.pg_namespace
        WHERE nspname ~ '^(ontology|entity)[1-9][0-9]*$'
    LOOP
        EXECUTE pg_catalog.format('ALTER SCHEMA %I OWNER TO ontology_age_graph_maintainer', graph_schema.nspname);
        -- 既有标签表不应继续由 Java 登录账号持有；应用只保留图 schema 内的必需操作权。
        EXECUTE pg_catalog.format('REVOKE ALL PRIVILEGES ON SCHEMA %I FROM PUBLIC', graph_schema.nspname);
        EXECUTE pg_catalog.format('GRANT USAGE, CREATE ON SCHEMA %I TO ontology_app_runtime', graph_schema.nspname);
        -- AGE 标签表由 NOLOGIN 运行组持有；运行账号仅能在该图库 schema 内创建图对象。
        FOR object_record IN
            SELECT c.relname, c.relkind
            FROM pg_catalog.pg_class c
            JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = graph_schema.nspname AND c.relkind IN ('r', 'p', 'S')
              AND (c.relkind <> 'S' OR NOT EXISTS (
                  SELECT 1 FROM pg_catalog.pg_depend d
                  WHERE d.classid = 'pg_class'::regclass AND d.objid = c.oid
                    AND d.refclassid = 'pg_class'::regclass AND d.deptype IN ('a', 'i')))
        LOOP
            IF object_record.relkind = 'S' THEN
                EXECUTE pg_catalog.format('ALTER SEQUENCE %I.%I OWNER TO ontology_app_runtime',
                                          graph_schema.nspname, object_record.relname);
            ELSE
                EXECUTE pg_catalog.format('ALTER TABLE %I.%I OWNER TO ontology_app_runtime',
                                          graph_schema.nspname, object_record.relname);
            END IF;
        END LOOP;
        EXECUTE pg_catalog.format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA %I '
            || 'TO ontology_app_runtime', graph_schema.nspname);
        EXECUTE pg_catalog.format('GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA %I '
            || 'TO ontology_app_runtime', graph_schema.nspname);
        EXECUTE pg_catalog.format(
            'ALTER DEFAULT PRIVILEGES FOR ROLE ontology_age_graph_maintainer IN SCHEMA %I '
            || 'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ontology_app_runtime', graph_schema.nspname);
        EXECUTE pg_catalog.format(
            'ALTER DEFAULT PRIVILEGES FOR ROLE ontology_age_graph_maintainer IN SCHEMA %I '
            || 'GRANT USAGE, SELECT ON SEQUENCES TO ontology_app_runtime', graph_schema.nspname);
    END LOOP;
END;
$$;

-- 清除历史 AGE 对象对登录账号和 PUBLIC 的直接 ACL；权限只保留在不可登录角色组。
SELECT pg_catalog.format('REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA %I FROM %I', n.nspname, :'runtime_login')
FROM pg_catalog.pg_namespace n WHERE n.nspname ~ '^(ontology|entity)[1-9][0-9]*$'
\gexec
SELECT pg_catalog.format('REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA %I FROM %I', n.nspname, :'runtime_login')
FROM pg_catalog.pg_namespace n WHERE n.nspname ~ '^(ontology|entity)[1-9][0-9]*$'
\gexec
SELECT pg_catalog.format('REVOKE ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA %I FROM %I', n.nspname, :'runtime_login')
FROM pg_catalog.pg_namespace n WHERE n.nspname ~ '^(ontology|entity)[1-9][0-9]*$'
\gexec
SELECT pg_catalog.format('REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA %I FROM PUBLIC', n.nspname)
FROM pg_catalog.pg_namespace n WHERE n.nspname ~ '^(ontology|entity)[1-9][0-9]*$'
\gexec
SELECT pg_catalog.format('REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA %I FROM PUBLIC', n.nspname)
FROM pg_catalog.pg_namespace n WHERE n.nspname ~ '^(ontology|entity)[1-9][0-9]*$'
\gexec
SELECT pg_catalog.format('REVOKE ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA %I FROM PUBLIC', n.nspname)
FROM pg_catalog.pg_namespace n WHERE n.nspname ~ '^(ontology|entity)[1-9][0-9]*$'
\gexec

-- 移除旧运行登录账号曾直接持有的图 schema ACL，权限只经 NOLOGIN 运行组继承。
SELECT pg_catalog.format('REVOKE ALL PRIVILEGES ON SCHEMA %I FROM %I', n.nspname, :'runtime_login')
FROM pg_catalog.pg_namespace n
WHERE n.nspname ~ '^(ontology|entity)[1-9][0-9]*$'
\gexec

SELECT (
    NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles
                WHERE rolname IN ('ontology_app_runtime', :'runtime_login')
                  AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolbypassrls OR rolreplication))
    AND NOT pg_catalog.has_database_privilege(:'runtime_login', pg_catalog.current_database(), 'CREATE')
    AND NOT pg_catalog.has_database_privilege(:'runtime_login', pg_catalog.current_database(), 'TEMP')
    AND NOT pg_catalog.has_schema_privilege(:'runtime_login', 'ontology', 'CREATE')
    AND pg_catalog.pg_has_role(:'runtime_login', 'ontology_app_runtime', 'MEMBER')
    AND NOT pg_catalog.pg_has_role(:'runtime_login', 'ontology_app_runtime', 'USAGE')
    AND EXISTS (
        SELECT 1 FROM pg_catalog.pg_db_role_setting s
        WHERE s.setrole = (SELECT oid FROM pg_catalog.pg_roles WHERE rolname = :'runtime_login')
          AND s.setdatabase = (SELECT oid FROM pg_catalog.pg_database WHERE datname = pg_catalog.current_database())
          AND 'role=ontology_app_runtime' = ANY(s.setconfig)
    )
    AND NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE (n.nspname = 'ontology' OR n.nspname ~ '^(ontology|entity)[1-9][0-9]*$')
          AND c.relowner = (SELECT oid FROM pg_catalog.pg_roles WHERE rolname = :'runtime_login')
    )
    AND NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_proc p
        JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace
        WHERE (n.nspname = 'ontology' OR n.nspname ~ '^(ontology|entity)[1-9][0-9]*$')
          AND p.proowner = (SELECT oid FROM pg_catalog.pg_roles WHERE rolname = :'runtime_login')
    )
    AND NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_namespace n
        CROSS JOIN LATERAL pg_catalog.aclexplode(COALESCE(n.nspacl,
            pg_catalog.acldefault('n'::"char", n.nspowner))) a
        WHERE (n.nspname = 'ontology' OR n.nspname ~ '^(ontology|entity)[1-9][0-9]*$')
          AND (a.grantee = (SELECT oid FROM pg_catalog.pg_roles WHERE rolname = :'runtime_login') OR a.grantee = 0)
    )
    AND NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        CROSS JOIN LATERAL pg_catalog.aclexplode(COALESCE(c.relacl,
            pg_catalog.acldefault(CASE WHEN c.relkind = 'S' THEN 's'::"char" ELSE 'r'::"char" END,
                                  c.relowner))) a
        WHERE (n.nspname = 'ontology' OR n.nspname ~ '^(ontology|entity)[1-9][0-9]*$')
          AND (a.grantee = (SELECT oid FROM pg_catalog.pg_roles WHERE rolname = :'runtime_login') OR a.grantee = 0)
    )
    AND NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_proc p
        JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace
        CROSS JOIN LATERAL pg_catalog.aclexplode(COALESCE(p.proacl,
            pg_catalog.acldefault('f'::"char", p.proowner))) a
        WHERE (n.nspname = 'ontology' OR n.nspname ~ '^(ontology|entity)[1-9][0-9]*$')
          AND (a.grantee = (SELECT oid FROM pg_catalog.pg_roles WHERE rolname = :'runtime_login') OR a.grantee = 0)
    )
) AS runtime_policy_ok
\gset
\if :runtime_policy_ok
\else
  \echo 'runtime role policy check failed'
  \quit 1
\endif
