package com.devin.ontology.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.devin.ontology.common.constant.I18nConstant;
import com.devin.ontology.common.exception.BusinessException;
import com.devin.ontology.common.utils.IdGenerator;
import com.devin.ontology.common.utils.i18n.I18nUtils;
import com.devin.ontology.dao.OntologyDao;
import com.devin.ontology.dao.OntologyRelationshipDao;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Date;
import java.util.Objects;

/**
 * 2025/11/19 19:22:03.
 *
 * <p>
 *  本体对象关系(OntologyRelationship)Entity层
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@TableName(value = "og_ontology_relationship")
public class OntologyRelationship implements Serializable {
    @Serial
    private static final long serialVersionUID = 385936487680508918L;
    
    /**
     * 关系id.
     */
    @TableId
    private BigInteger id; 

    /**
     * 源节点id.
     */
    @TableField("source_node_id")
    private BigInteger sourceNodeId;      

    /**
     * 目标节点id.
     */
    @TableField("target_node_id")
    private BigInteger targetNodeId;

    /**
     * 图谱id.
     */
    @TableField("graph_id")
    private BigInteger graphId;

    /**
     * 关系名称.
     */
    @TableField("name")
    private String name;      

    /**
     * 关系描述.
     */
    @TableField("description")
    private String description;      

    /**
     * 本体关系属性Schema（实体关系属性JSON格式）.
     */
    @TableField("property_schema_json")
    private String propertySchemaJson;      

    /**
     * 创建时间.
     */
    @TableField("create_time")
    private Date createTime;      

    /**
     * 更新时间.
     */
    @TableField("update_time")
    private Date updateTime;      

    /**
     * 初始化.
     */
    public void init() {
        this.createTime = new Date();
        this.updateTime = new Date();
    }

    /**
     * 保存.
     */
    public void save() {
        checkNodeNameDuplicate();
        OntologyRelationshipDao relationshipDao = SpringContextHolder.getBean(OntologyRelationshipDao.class);
        setId(IdGenerator.generateKey());
        init();
        relationshipDao.save(this);
    }

    /**
     * 更新.
     */
    public void update() {
        checkNodeNameDuplicate();
        OntologyRelationshipDao relationshipDao = SpringContextHolder.getBean(OntologyRelationshipDao.class);
        setUpdateTime(new Date());
        relationshipDao.save(this);
    }

    /**
     * 检查关系名称是否重复.
     */
    public void checkNodeNameDuplicate() {
        OntologyDao ontologyDao = SpringContextHolder.getBean(OntologyDao.class);
        Ontology ontology = ontologyDao.getByGraphIdAndName(getGraphId(), getName());
        if (Objects.nonNull(ontology)) {
            throw new BusinessException(I18nUtils.getMessage(I18nConstant.NODE_RELATIONSHIP_NAME_DUPLICATE, getName()));
        }
    }
}
