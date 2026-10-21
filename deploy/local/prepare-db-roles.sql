\set ON_ERROR_STOP on
\getenv runtime_login ONTOLOGY_DB_USER
\getenv runtime_password ONTOLOGY_DB_PASSWORD
\getenv migrator_password ONTOLOGY_MIGRATOR_PASSWORD
\getenv bootstrap_login ONTOLOGY_BOOTSTRAP_DB_USER
\getenv bootstrap_password ONTOLOGY_BOOTSTRAP_DB_PASSWORD

SELECT (:'runtime_login' <> :'bootstrap_login') AS runtime_and_bootstrap_are_separate \gset
\if :runtime_and_bootstrap_are_separate
\else
  \echo 'runtime and bootstrap database logins must be different'
  \quit 1
\endif

-- 仅由数据库管理员执行：创建分离角色，并将现有应用对象交给迁移账号管理。
SELECT pg_catalog.format(
    'CREATE ROLE ontology_app_runtime NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS NOREPLICATION')
WHERE NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'ontology_app_runtime')
\gexec
ALTER ROLE ontology_app_runtime WITH NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS NOREPLICATION;

SELECT pg_catalog.format(
    'CREATE ROLE ontology_age_graph_maintainer NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS NOREPLICATION')
WHERE NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'ontology_age_graph_maintainer')
\gexec
ALTER ROLE ontology_age_graph_maintainer WITH NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS NOREPLICATION;

SELECT pg_catalog.format(
    'CREATE ROLE ontology_audit_maintenance NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS NOREPLICATION')
WHERE NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'ontology_audit_maintenance')
\gexec
ALTER ROLE ontology_audit_maintenance WITH NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS NOREPLICATION;

SELECT pg_catalog.format(
    'CREATE ROLE ontology_bootstrap_maintenance NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS NOREPLICATION')
WHERE NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'ontology_bootstrap_maintenance')
\gexec
ALTER ROLE ontology_bootstrap_maintenance WITH NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS NOREPLICATION;

SELECT pg_catalog.format(
    'CREATE ROLE ontology_migrator LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS NOREPLICATION PASSWORD %L',
    :'migrator_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'ontology_migrator')
\gexec
SELECT pg_catalog.format(
    'ALTER ROLE ontology_migrator WITH LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS NOREPLICATION PASSWORD %L',
    :'migrator_password')
\gexec

SELECT pg_catalog.format(
    'CREATE ROLE %I LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS NOREPLICATION PASSWORD %L',
    :'runtime_login', :'runtime_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = :'runtime_login')
\gexec
SELECT pg_catalog.format(
    'ALTER ROLE %I WITH LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS NOREPLICATION',
    :'runtime_login')
\gexec

SELECT pg_catalog.format(
    'CREATE ROLE %I LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS NOREPLICATION PASSWORD %L',
    :'bootstrap_login', :'bootstrap_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = :'bootstrap_login')
\gexec
SELECT pg_catalog.format(
    'ALTER ROLE %I WITH LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS NOREPLICATION PASSWORD %L',
    :'bootstrap_login', :'bootstrap_password')
\gexec
GRANT ontology_bootstrap_maintenance TO :"bootstrap_login" WITH INHERIT FALSE, SET TRUE;
-- Bootstrap 登录凭据仅能切换到窄权限组；日常运行身份没有 ledger 访问权。
SELECT pg_catalog.format('ALTER ROLE %I IN DATABASE %I SET role TO %L',
                         :'bootstrap_login', pg_catalog.current_database(), 'ontology_bootstrap_maintenance')
\gexec

GRANT ontology_app_runtime TO :"runtime_login" WITH INHERIT FALSE, SET TRUE;
-- 登录身份只负责连接；每个新会话自动切换到不可登录的运行角色，避免 AGE 新对象由登录账号持有。
SELECT pg_catalog.format('ALTER ROLE %I IN DATABASE %I SET role TO %L',
                         :'runtime_login', pg_catalog.current_database(), 'ontology_app_runtime')
\gexec
-- 迁移窗口临时加入 owner 角色；迁移完成后由 finalize 脚本撤销。
GRANT ontology_app_runtime TO ontology_migrator WITH INHERIT TRUE, SET TRUE;
GRANT ontology_age_graph_maintainer TO ontology_migrator;
GRANT ontology_audit_maintenance TO ontology_migrator WITH INHERIT TRUE, SET TRUE;
-- AGE 的图目录表要求业务角色具备 owner 身份；该 NOLOGIN helper 仅在安全函数内转交目录表 owner。
GRANT ontology_app_runtime TO ontology_age_graph_maintainer WITH INHERIT FALSE, SET TRUE;

SELECT pg_catalog.format('REVOKE CREATE, TEMP ON DATABASE %I FROM PUBLIC, ontology_app_runtime, '
                         || 'ontology_migrator, ontology_bootstrap_maintenance, %I, %I',
                         pg_catalog.current_database(), :'runtime_login', :'bootstrap_login')
\gexec
SELECT pg_catalog.format('GRANT CONNECT ON DATABASE %I TO ontology_migrator, %I, %I',
                         pg_catalog.current_database(), :'runtime_login', :'bootstrap_login')
\gexec
SELECT pg_catalog.format('GRANT CREATE ON DATABASE %I TO ontology_age_graph_maintainer',
                         pg_catalog.current_database())
\gexec

SELECT pg_catalog.format('CREATE SCHEMA ontology AUTHORIZATION ontology_migrator')
WHERE NOT EXISTS (SELECT 1 FROM pg_catalog.pg_namespace WHERE nspname = 'ontology')
\gexec
ALTER SCHEMA ontology OWNER TO ontology_migrator;
REVOKE CREATE ON SCHEMA ontology FROM PUBLIC, ontology_app_runtime;

-- 保留审计清理函数的专用 owner；其他现存 ontology 对象统一归迁移账号所有。
DO $$
DECLARE
    object_record RECORD;
BEGIN
    FOR object_record IN
        SELECT c.relname, c.relkind
        FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'ontology' AND c.relkind IN ('r', 'p', 'v', 'm', 'f', 'S')
          -- 依附于表的序列随表 owner 自动转移，不能与所属表分开改 owner。
          AND (c.relkind <> 'S' OR NOT EXISTS (
              SELECT 1 FROM pg_catalog.pg_depend d
              WHERE d.classid = 'pg_class'::regclass AND d.objid = c.oid
                AND d.refclassid = 'pg_class'::regclass AND d.deptype IN ('a', 'i')))
    LOOP
        IF object_record.relkind = 'S' THEN
            EXECUTE pg_catalog.format('ALTER SEQUENCE ontology.%I OWNER TO ontology_migrator', object_record.relname);
        ELSIF object_record.relkind = 'v' THEN
            EXECUTE pg_catalog.format('ALTER VIEW ontology.%I OWNER TO ontology_migrator', object_record.relname);
        ELSIF object_record.relkind = 'm' THEN
            EXECUTE pg_catalog.format('ALTER MATERIALIZED VIEW ontology.%I OWNER TO ontology_migrator',
                                      object_record.relname);
        ELSIF object_record.relkind = 'f' THEN
            EXECUTE pg_catalog.format('ALTER FOREIGN TABLE ontology.%I OWNER TO ontology_migrator',
                                      object_record.relname);
        ELSE
            EXECUTE pg_catalog.format('ALTER TABLE ontology.%I OWNER TO ontology_migrator', object_record.relname);
        END IF;
    END LOOP;

    FOR object_record IN
        SELECT p.oid::pg_catalog.regprocedure AS identity, p.prokind
        FROM pg_catalog.pg_proc p
        JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'ontology'
          AND p.proname NOT IN ('prune_expired_audit_events', 'create_age_graph', 'drop_age_graph')
    LOOP
        IF object_record.prokind = 'p' THEN
            EXECUTE pg_catalog.format('ALTER PROCEDURE %s OWNER TO ontology_migrator', object_record.identity);
        ELSE
            EXECUTE pg_catalog.format('ALTER FUNCTION %s OWNER TO ontology_migrator', object_record.identity);
        END IF;
    END LOOP;
END;
$$;

-- 既有 AGE 标签对象先交由迁移账号管理，0.0.12 迁移再转给 NOLOGIN 运行角色。
DO $$
DECLARE
    graph_schema RECORD;
    graph_object RECORD;
BEGIN
    FOR graph_schema IN
        SELECT nspname FROM pg_catalog.pg_namespace
        WHERE nspname ~ '^(ontology|entity)[1-9][0-9]*$'
    LOOP
        FOR graph_object IN
            SELECT c.relname, c.relkind
            FROM pg_catalog.pg_class c
            JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = graph_schema.nspname AND c.relkind IN ('r', 'p', 'S')
              -- 表 owner 变更会同时交接其自增序列；避免对依附序列重复执行 ALTER SEQUENCE。
              AND (c.relkind <> 'S' OR NOT EXISTS (
                  SELECT 1 FROM pg_catalog.pg_depend d
                  WHERE d.classid = 'pg_class'::regclass AND d.objid = c.oid
                    AND d.refclassid = 'pg_class'::regclass AND d.deptype IN ('a', 'i')))
        LOOP
            IF graph_object.relkind = 'S' THEN
                EXECUTE pg_catalog.format('ALTER SEQUENCE %I.%I OWNER TO ontology_migrator',
                                          graph_schema.nspname, graph_object.relname);
            ELSE
                EXECUTE pg_catalog.format('ALTER TABLE %I.%I OWNER TO ontology_migrator',
                                          graph_schema.nspname, graph_object.relname);
            END IF;
        END LOOP;
    END LOOP;
END;
$$;

-- 旧 AGE 图 schema 转交给不可登录角色；应用只获图内操作权限，不再获数据库级 CREATE。
GRANT USAGE ON SCHEMA ag_catalog TO ontology_app_runtime, ontology_age_graph_maintainer;
GRANT USAGE ON TYPE ag_catalog.agtype TO ontology_app_runtime;
GRANT EXECUTE ON FUNCTION ag_catalog.create_graph(name) TO ontology_age_graph_maintainer;
GRANT EXECUTE ON FUNCTION ag_catalog.drop_graph(name, boolean) TO ontology_age_graph_maintainer;
DO $$
DECLARE
    graph_schema RECORD;
BEGIN
    FOR graph_schema IN
        SELECT nspname FROM pg_catalog.pg_namespace
        WHERE nspname ~ '^(ontology|entity)[1-9][0-9]*$'
    LOOP
        EXECUTE pg_catalog.format('ALTER SCHEMA %I OWNER TO ontology_age_graph_maintainer', graph_schema.nspname);
        EXECUTE pg_catalog.format('GRANT USAGE, CREATE ON SCHEMA %I TO ontology_app_runtime', graph_schema.nspname);
        EXECUTE pg_catalog.format(
            'ALTER DEFAULT PRIVILEGES FOR ROLE ontology_age_graph_maintainer IN SCHEMA %I '
            || 'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ontology_app_runtime', graph_schema.nspname);
        EXECUTE pg_catalog.format(
            'ALTER DEFAULT PRIVILEGES FOR ROLE ontology_age_graph_maintainer IN SCHEMA %I '
            || 'GRANT USAGE, SELECT ON SEQUENCES TO ontology_app_runtime', graph_schema.nspname);
    END LOOP;
END;
$$;

-- 默认权限与 Flyway history 初始化顺序一致；后续版本迁移仍由 ontology_migrator 执行。
ALTER DEFAULT PRIVILEGES FOR ROLE ontology_migrator IN SCHEMA ontology
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ontology_app_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE ontology_migrator IN SCHEMA ontology
  GRANT USAGE, SELECT ON SEQUENCES TO ontology_app_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE ontology_migrator
  REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
