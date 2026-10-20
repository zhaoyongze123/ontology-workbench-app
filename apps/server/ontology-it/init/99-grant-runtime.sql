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
