-- AGE 的 graphid_ops 位于 ag_catalog；受限建图函数需要把该受信任 schema 加入自身搜索路径。
-- ontology_migrator 是该 schema 的 owner；迁移只临时授予 owner role CREATE，再在事务内切换角色。
GRANT CREATE ON SCHEMA ontology TO ontology_age_graph_maintainer;
SET LOCAL ROLE ontology_age_graph_maintainer;
CREATE OR REPLACE FUNCTION ontology.create_age_graph(p_graph_name TEXT)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, ag_catalog
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
RESET ROLE;
REVOKE CREATE ON SCHEMA ontology FROM ontology_age_graph_maintainer;
