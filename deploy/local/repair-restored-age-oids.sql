\set ON_ERROR_STOP on

-- AGE 1.6.0-rc0 的 graphid/label.graph 是裸 OID，逻辑恢复不会按 schema 名重定位。
-- 只在显式 restore 的新目标库运行；上游问题与成因：https://github.com/apache/age/issues/2503
BEGIN;
LOCK TABLE ag_catalog.ag_graph, ag_catalog.ag_label IN ACCESS EXCLUSIVE MODE;

DO $$
DECLARE
    graph_table REGCLASS := 'ag_catalog.ag_graph'::regclass;
    label_table REGCLASS := 'ag_catalog.ag_label'::regclass;
    graph_id_att SMALLINT;
    label_graph_att SMALLINT;
    fk_count INTEGER;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_extension WHERE extname = 'age') THEN
        RAISE EXCEPTION 'AGE extension is not installed in restore target';
    END IF;

    -- 拒绝在目录表结构未知或恢复对象未落到对应图库 schema 时做任何修复。
    IF EXISTS (
        SELECT 1
        FROM ag_catalog.ag_graph g
        LEFT JOIN pg_catalog.pg_namespace n ON n.oid = g.namespace::oid
        WHERE n.oid IS NULL OR n.nspname IS DISTINCT FROM g.name::text
    ) THEN
        RAISE EXCEPTION 'AGE graph namespace does not match its restored graph schema';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM ag_catalog.ag_label l
        LEFT JOIN ag_catalog.ag_graph g ON g.graphid = l.graph
        LEFT JOIN pg_catalog.pg_class c ON c.oid = l.relation::oid
        LEFT JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE g.graphid IS NULL OR c.oid IS NULL OR n.oid IS DISTINCT FROM g.namespace::oid
    ) THEN
        RAISE EXCEPTION 'AGE label graph/relation association is inconsistent in restore target';
    END IF;

    SELECT a.attnum INTO graph_id_att
    FROM pg_catalog.pg_attribute a
    WHERE a.attrelid = graph_table AND a.attname = 'graphid' AND NOT a.attisdropped;
    SELECT a.attnum INTO label_graph_att
    FROM pg_catalog.pg_attribute a
    WHERE a.attrelid = label_table AND a.attname = 'graph' AND NOT a.attisdropped;
    SELECT count(*) INTO fk_count
    FROM pg_catalog.pg_constraint c
    WHERE c.conrelid = label_table AND c.confrelid = graph_table
      AND c.conname = 'fk_graph_oid' AND c.contype = 'f'
      AND NOT c.condeferrable AND c.convalidated
      AND c.conkey = ARRAY[label_graph_att]::SMALLINT[]
      AND c.confkey = ARRAY[graph_id_att]::SMALLINT[];
    IF fk_count <> 1 THEN
        RAISE EXCEPTION 'AGE fk_graph_oid constraint shape is unexpected; refusing catalog repair';
    END IF;
END
$$;

-- 临时 OID 同时高于所有旧 graphid 和新 namespace OID，避免图 A/B 的目标 OID
-- 与另一图的旧主键重叠时触发唯一索引冲突。若 OID 空间没有足够余量则安全中止。
CREATE TEMP TABLE ontology_restore_age_graph_oid_map ON COMMIT DROP AS
SELECT g.graphid AS old_graphid,
       g.namespace::oid AS target_graphid,
       row_number() OVER (ORDER BY g.graphid) AS ordinal,
       NULL::oid AS temporary_graphid
FROM ag_catalog.ag_graph g
WHERE g.graphid IS DISTINCT FROM g.namespace::oid;
ALTER TABLE ontology_restore_age_graph_oid_map
    ADD PRIMARY KEY (old_graphid), ADD UNIQUE (target_graphid), ADD UNIQUE (temporary_graphid);

DO $$
DECLARE
    oid_base BIGINT;
    mapping_count BIGINT;
BEGIN
    SELECT count(*) INTO mapping_count FROM ontology_restore_age_graph_oid_map;
    IF mapping_count = 0 THEN
        RETURN;
    END IF;

    SELECT GREATEST(COALESCE(max(g.graphid::BIGINT), 0),
                    COALESCE(max(g.namespace::oid::BIGINT), 0))
      INTO oid_base
      FROM ag_catalog.ag_graph g;
    IF oid_base + mapping_count > 4294967295 THEN
        RAISE EXCEPTION 'not enough temporary OID values to safely repair AGE graph catalog';
    END IF;

    UPDATE ontology_restore_age_graph_oid_map m
       SET temporary_graphid = (oid_base + m.ordinal)::oid;
END
$$;

DO $$
DECLARE
    mapping_count BIGINT;
BEGIN
    SELECT count(*) INTO mapping_count FROM ontology_restore_age_graph_oid_map;
    IF mapping_count = 0 THEN
        RAISE NOTICE 'AGE restore catalog is already consistent';
        RETURN;
    END IF;

    -- FK 不可延迟；锁定目录表后短暂拆除，以 old→temporary→target 两阶段改写。
    ALTER TABLE ag_catalog.ag_label DROP CONSTRAINT fk_graph_oid;
    UPDATE ag_catalog.ag_label l
       SET graph = m.temporary_graphid
      FROM ontology_restore_age_graph_oid_map m
     WHERE l.graph = m.old_graphid;
    UPDATE ag_catalog.ag_graph g
       SET graphid = m.temporary_graphid
      FROM ontology_restore_age_graph_oid_map m
     WHERE g.graphid = m.old_graphid;
    UPDATE ag_catalog.ag_label l
       SET graph = m.target_graphid
      FROM ontology_restore_age_graph_oid_map m
     WHERE l.graph = m.temporary_graphid;
    UPDATE ag_catalog.ag_graph g
       SET graphid = m.target_graphid
      FROM ontology_restore_age_graph_oid_map m
     WHERE g.graphid = m.temporary_graphid;
    ALTER TABLE ag_catalog.ag_label
        ADD CONSTRAINT fk_graph_oid FOREIGN KEY (graph) REFERENCES ag_catalog.ag_graph (graphid);
END
$$;

COMMIT;
