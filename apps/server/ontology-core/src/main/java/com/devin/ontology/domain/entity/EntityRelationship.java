package com.devin.ontology.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.devin.ontology.common.utils.IdGenerator;
import com.devin.ontology.dao.EntityRelationshipDao;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Date;

/**
 * 2025/11/19 22:06:57.
 *
 * <p>
 *  实体对象关系(EntityRelationship)Entity层
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@TableName(value = "og_entity_relationship")
public class EntityRelationship implements Serializable {
    @Serial
    private static final long serialVersionUID = 929523993342709699L;
    
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
     * 实体关系属性JSON.
     */
    @TableField("property_json")
    private String propertyJson;

    /**
     * 本体关系id.
     */
    @TableField("ontology_relationship_id")
    private BigInteger ontologyRelationshipId;

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
     * @return EntityRelationship
     */
    public EntityRelationship save() {
        EntityRelationshipDao relationshipDao = SpringContextHolder.getBean(EntityRelationshipDao.class);
        setId(IdGenerator.generateKey());
        init();
        relationshipDao.save(this);
        return this;
    }

    /**
     * 更新.
     */
    public void update() {
        EntityRelationshipDao relationshipDao = SpringContextHolder.getBean(EntityRelationshipDao.class);
        setUpdateTime(new Date());
        relationshipDao.save(this);
    }
}
