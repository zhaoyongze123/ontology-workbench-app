DROP TABLE IF EXISTS ontology.og_organization;
CREATE TABLE ontology.og_organization(
                                         id INT8 NOT NULL,
                                         name VARCHAR(200) NOT NULL,
                                         description VARCHAR(500),
                                         create_time TIMESTAMP NOT NULL DEFAULT NOW(),
                                         update_time TIMESTAMP NOT NULL DEFAULT NOW(),
                                         PRIMARY KEY (id)
);
COMMENT ON COLUMN ontology.og_organization.id IS '项目id';
COMMENT ON COLUMN ontology.og_organization.name IS '项目名称';
COMMENT ON COLUMN ontology.og_organization.description IS '项目描述';
COMMENT ON COLUMN ontology.og_organization.create_time IS '创建时间';
COMMENT ON COLUMN ontology.og_organization.update_time IS '更新时间';
COMMENT ON TABLE ontology.og_organization IS '组织项目';


DROP TABLE IF EXISTS ontology.og_graph;
CREATE TABLE ontology.og_graph(
                                  id INT8 NOT NULL,
                                  name VARCHAR(200) NOT NULL,
                                  description VARCHAR(500),
                                  type VARCHAR(10) NOT NULL DEFAULT 'ONTOLOGY',
                                  link_graph_id INT8,
                                  organization_id INT8 NOT NULL,
                                  create_time TIMESTAMP NOT NULL DEFAULT NOW(),
                                  update_time TIMESTAMP NOT NULL DEFAULT NOW(),
                                  PRIMARY KEY (id)
);
COMMENT ON COLUMN ontology.og_graph.id IS '图谱id';
COMMENT ON COLUMN ontology.og_graph.name IS '图谱名称';
COMMENT ON COLUMN ontology.og_graph.description IS '图谱描述';
COMMENT ON COLUMN ontology.og_graph.type IS '图谱类型（ONTOLOGY：本体图谱；ENTITY：实体图谱）';
COMMENT ON COLUMN ontology.og_graph.link_graph_id IS '实体图谱关联本体图谱id';
COMMENT ON COLUMN ontology.og_graph.organization_id IS '项目id';
COMMENT ON COLUMN ontology.og_graph.create_time IS '创建时间';
COMMENT ON COLUMN ontology.og_graph.update_time IS '更新时间';
COMMENT ON TABLE ontology.og_graph IS '图谱';


DROP TABLE IF EXISTS ontology.og_ontology;
CREATE TABLE ontology.og_ontology(
                                     id INT8 NOT NULL,
                                     name VARCHAR(200) NOT NULL,
                                     description VARCHAR(500),
                                     property_schema_json TEXT,
                                     graph_id INT8 NOT NULL,
                                     create_time TIMESTAMP NOT NULL DEFAULT NOW(),
                                     update_time TIMESTAMP NOT NULL DEFAULT NOW(),
                                     PRIMARY KEY (id)
);
COMMENT ON COLUMN ontology.og_ontology.id IS '本体id';
COMMENT ON COLUMN ontology.og_ontology.name IS '本体名称';
COMMENT ON COLUMN ontology.og_ontology.description IS '本体描述';
COMMENT ON COLUMN ontology.og_ontology.property_schema_json IS '本体属性Schema（实体JSON格式）';
COMMENT ON COLUMN ontology.og_ontology.graph_id IS '图谱id';
COMMENT ON COLUMN ontology.og_ontology.create_time IS '创建时间';
COMMENT ON COLUMN ontology.og_ontology.update_time IS '更新时间';
COMMENT ON TABLE ontology.og_ontology IS '本体对象';


DROP TABLE IF EXISTS ontology.og_entity;
CREATE TABLE ontology.og_entity(
                                   id INT8 NOT NULL,
                                   name VARCHAR(200) NOT NULL,
                                   description VARCHAR(500),
                                   property_json TEXT,
                                   ontology_id INT8 NOT NULL,
                                   graph_id INT8 NOT NULL,
                                   create_time TIMESTAMP NOT NULL DEFAULT NOW(),
                                   update_time TIMESTAMP NOT NULL DEFAULT NOW(),
                                   PRIMARY KEY (id)
);
COMMENT ON COLUMN ontology.og_entity.id IS '实体id';
COMMENT ON COLUMN ontology.og_entity.name IS '实体名称';
COMMENT ON COLUMN ontology.og_entity.description IS '实体描述';
COMMENT ON COLUMN ontology.og_entity.property_json IS '实体属性JSON';
COMMENT ON COLUMN ontology.og_entity.ontology_id IS '本体id';
COMMENT ON COLUMN ontology.og_entity.graph_id IS '图谱id';
COMMENT ON COLUMN ontology.og_entity.create_time IS '创建时间';
COMMENT ON COLUMN ontology.og_entity.update_time IS '更新时间';
COMMENT ON TABLE ontology.og_entity IS '实体对象';


DROP TABLE IF EXISTS ontology.og_ontology_relationship;
CREATE TABLE ontology.og_ontology_relationship(
                                                  id INT8 NOT NULL,
                                                  source_node_id INT8 NOT NULL,
                                                  target_node_id INT8 NOT NULL,
                                                  graph_id INT8 NOT NULL,
                                                  name VARCHAR(200) NOT NULL,
                                                  description VARCHAR(500),
                                                  property_schema_json TEXT,
                                                  create_time TIMESTAMP NOT NULL DEFAULT NOW(),
                                                  update_time TIMESTAMP NOT NULL DEFAULT NOW(),
                                                  PRIMARY KEY (id)
);
COMMENT ON COLUMN ontology.og_ontology_relationship.id IS '关系id';
COMMENT ON COLUMN ontology.og_ontology_relationship.source_node_id IS '源节点id';
COMMENT ON COLUMN ontology.og_ontology_relationship.target_node_id IS '目标节点id';
COMMENT ON COLUMN ontology.og_ontology_relationship.graph_id IS '图谱id';
COMMENT ON COLUMN ontology.og_ontology_relationship.name IS '关系名称';
COMMENT ON COLUMN ontology.og_ontology_relationship.description IS '关系描述';
COMMENT ON COLUMN ontology.og_ontology_relationship.property_schema_json IS '本体关系属性Schema（实体关系属性JSON格式）';
COMMENT ON COLUMN ontology.og_ontology_relationship.create_time IS '创建时间';
COMMENT ON COLUMN ontology.og_ontology_relationship.update_time IS '更新时间';
COMMENT ON TABLE ontology.og_ontology_relationship IS '本体对象关系';


DROP TABLE IF EXISTS ontology.og_entity_relationship;
CREATE TABLE ontology.og_entity_relationship(
                                                id INT8 NOT NULL,
                                                source_node_id INT8 NOT NULL,
                                                target_node_id INT8 NOT NULL,
                                                graph_id INT8 NOT NULL,
                                                name VARCHAR(200) NOT NULL,
                                                description VARCHAR(500),
                                                property_json TEXT,
                                                ontology_relationship_id INT8 NOT NULL,
                                                create_time TIMESTAMP NOT NULL DEFAULT NOW(),
                                                update_time TIMESTAMP NOT NULL DEFAULT NOW(),
                                                PRIMARY KEY (id)
);
COMMENT ON COLUMN ontology.og_entity_relationship.id IS '关系id';
COMMENT ON COLUMN ontology.og_entity_relationship.source_node_id IS '源节点id';
COMMENT ON COLUMN ontology.og_entity_relationship.target_node_id IS '目标节点id';
COMMENT ON COLUMN ontology.og_entity_relationship.graph_id IS '图谱id';
COMMENT ON COLUMN ontology.og_entity_relationship.name IS '关系名称';
COMMENT ON COLUMN ontology.og_entity_relationship.description IS '关系描述';
COMMENT ON COLUMN ontology.og_entity_relationship.property_json IS '实体关系属性JSON';
COMMENT ON COLUMN ontology.og_entity_relationship.ontology_relationship_id IS '本体关系id';
COMMENT ON COLUMN ontology.og_entity_relationship.create_time IS '创建时间';
COMMENT ON COLUMN ontology.og_entity_relationship.update_time IS '更新时间';
COMMENT ON TABLE ontology.og_entity_relationship IS '实体对象关系';