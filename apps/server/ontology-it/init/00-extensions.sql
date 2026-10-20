-- 仅在一次性隔离测试数据库中安装 AGE 与向量扩展。
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS age;
ALTER DATABASE ontology_multiuser_dev_20261020
  SET search_path = ag_catalog, "$user", public;
