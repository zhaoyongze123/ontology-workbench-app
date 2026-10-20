package com.devin.ontology.model;

import com.devin.ontology.domain.entity.Entity;
import com.devin.ontology.domain.entity.EntityRelationship;
import com.devin.ontology.domain.entity.Graph;
import com.devin.ontology.domain.entity.Ontology;
import com.devin.ontology.domain.entity.OntologyRelationship;
import lombok.Data;
import java.util.List;

/**
 * 2025/11/22 11:25.
 *
 * <p>
 *     图谱Json信息
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
public class JsonGraphInfo {

    /**
     * 图谱.
     */
    private Graph graph;

    /**
     * 本体节点列表.
     */
    private List<Ontology> ontologyList;

    /**
     * 实体节点列表.
     */
    private List<Entity> entityList;

    /**
     * 本体关系列表.
     */
    private List<OntologyRelationship> ontologyRelationshipList;

    /**
     * 实体关系列表.
     */
    private List<EntityRelationship> entityRelationshipList;
}
