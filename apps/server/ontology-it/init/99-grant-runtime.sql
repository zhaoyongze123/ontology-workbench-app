-- 仅授予测试运行账号访问应用 schema、AGE 图 schema 和业务表所需权限。
GRANT USAGE, CREATE ON SCHEMA ontology TO ontology_it_runtime;
GRANT USAGE ON SCHEMA ag_catalog TO ontology_it_runtime;
GRANT USAGE ON TYPE ag_catalog.agtype TO ontology_it_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER
  ON ALL TABLES IN SCHEMA ontology TO ontology_it_runtime;
GRANT USAGE, SELECT, UPDATE
  ON ALL SEQUENCES IN SCHEMA ontology TO ontology_it_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE ontology_it_admin IN SCHEMA ontology
  GRANT SELECT, INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER ON TABLES TO ontology_it_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE ontology_it_admin IN SCHEMA ontology
  GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO ontology_it_runtime;
-- 运行账号可追加和查看审计，但不能改写、删除、截断审计或配置/调用保留清理策略。
REVOKE UPDATE, DELETE, TRUNCATE ON TABLE ontology.og_audit_event FROM ontology_it_runtime;
REVOKE ALL ON TABLE ontology.og_audit_retention_policy FROM ontology_it_runtime;
REVOKE ALL ON FUNCTION ontology.prune_expired_audit_events() FROM PUBLIC, ontology_it_runtime;
