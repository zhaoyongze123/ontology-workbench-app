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
 * 2025/11/16 18:32:57.
 *
 * <p>
 *  本体对象(Ontology)Entity层
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@TableName(value = "og_ontology")
public class Ontology implements Serializable {
    @Serial
    private static final long serialVersionUID = 781389190951169805L;
    
    /**
     * 本体id.
     */
    @TableId
    private BigInteger id; 

    /**
     * 本体名称.
     */
    @TableField("name")
    private String name;      

    /**
     * 本体描述.
     */
    @TableField("description")
    private String description;      

    /**
     * 本体属性Schema（实体JSON格式）.
     */
    @TableField("property_schema_json")
    private String propertySchemaJson;

    /**
     * 图谱id.
     */
    @TableField("graph_id")
    private BigInteger graphId;      

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
     * @return Ontology
     */
    public Ontology save() {
        OntologyDao ontologyDao = SpringContextHolder.getBean(OntologyDao.class);
        checkDuplicateRelationship();
        setId(IdGenerator.generateKey());
        init();
        ontologyDao.save(this);
        return this;
    }

    /**
     * 更新节点.
     */
    public void update() {
        checkDuplicateRelationship();
        OntologyDao ontologyDao = SpringContextHolder.getBean(OntologyDao.class);
        setUpdateTime(new Date());
        ontologyDao.updateById(this);
    }

    /**
     * 检查本体关系名称是否重复.
     */
    public void checkDuplicateRelationship() {
        OntologyRelationshipDao relationshipDao = SpringContextHolder.getBean(OntologyRelationshipDao.class);
        // 检查是否有重名本体关系
        OntologyRelationship relationship = relationshipDao.getByGraphIdAndName(getGraphId(), getName());
        if (Objects.nonNull(relationship)) {
            throw new BusinessException(I18nUtils.getMessage(I18nConstant.NODE_RELATIONSHIP_NAME_DUPLICATE, getName()));
        }
    }
}
