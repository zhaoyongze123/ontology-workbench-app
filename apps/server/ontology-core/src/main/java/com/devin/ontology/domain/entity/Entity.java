package com.devin.ontology.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.devin.ontology.common.utils.IdGenerator;
import com.devin.ontology.dao.EntityDao;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Date;

/**
 * 2025/11/19 23:17:57.
 *
 * <p>
 *  实体对象(Entity)Entity层
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@TableName(value = "og_entity")
public class Entity implements Serializable {
    @Serial
    private static final long serialVersionUID = 633296048608856533L;
    
    /**
     * 实体id.
     */
    @TableId
    private BigInteger id; 

    /**
     * 实体名称.
     */
    @TableField("name")
    private String name;      

    /**
     * 实体描述.
     */
    @TableField("description")
    private String description;      

    /**
     * 实体属性JSON.
     */
    @TableField("property_json")
    private String propertyJson;      

    /**
     * 本体id.
     */
    @TableField("ontology_id")
    private BigInteger ontologyId;      

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
     * @return 保存信息
     */
    public Entity save() {
        EntityDao entityDao = SpringContextHolder.getBean(EntityDao.class);
        setId(IdGenerator.generateKey());
        init();
        entityDao.save(this);
        return this;
    }

    /**
     * 更新节点.
     */
    public void update() {
        EntityDao entityDao = SpringContextHolder.getBean(EntityDao.class);
        setUpdateTime(new Date());
        entityDao.updateById(this);
    }
}
