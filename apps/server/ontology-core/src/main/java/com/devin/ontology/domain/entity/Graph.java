package com.devin.ontology.domain.entity;

import com.devin.ontology.common.utils.IdGenerator;
import com.devin.ontology.dao.GraphDao;
import com.devin.ontology.dao.OrganizationDao;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import lombok.Data;
import java.math.BigInteger;
import java.util.Date;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * 2025/11/16 00:42:30.
 *
 * <p>
 * 图谱(Graph)Entity层
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@TableName(value = "og_graph")
public class Graph implements Serializable {
    @Serial
    private static final long serialVersionUID = -36107128770674984L;

    /**
     * 图谱id.
     */
    @TableId
    private BigInteger id;

    /**
     * 图谱名称.
     */
    @TableField("name")
    private String name;

    /**
     * 图谱描述.
     */
    @TableField("description")
    private String description;

    /**
     * 图谱类型（ONTOLOGY: 本体图谱；ENTITY: 实体图谱）.
     */
    @TableField("type")
    private String type;

    /**
     * 实体图谱关联本体图谱id.
     */
    @TableField("link_graph_id")
    private BigInteger linkGraphId;

    /**
     * 项目id.
     */
    @TableField("organization_id")
    private BigInteger organizationId;

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
     * 链接的ONTOLOGY图谱.
     */
    @TableField(exist = false)
    private Graph linkOntologyGraph;

    /**
     * 初始化.
     */
    public void init() {
        this.createTime = new Date();
        this.updateTime = new Date();
    }

    /**
     * 保存.
     *
     * @return Graph
     */
    public Graph save() {
        GraphDao graphDao = SpringContextHolder.getBean(GraphDao.class);
        OrganizationDao organizationDao = SpringContextHolder.getBean(OrganizationDao.class);
        setId(IdGenerator.generateKey());
        this.init();
        graphDao.save(this);
        // 更新项目时间
        organizationDao.updateTime(this.organizationId);
        return this;
    }

    /**
     * 更新.
     */
    public void update() {
        GraphDao graphDao = SpringContextHolder.getBean(GraphDao.class);
        LambdaUpdateChainWrapper<Graph> graphWrapper = graphDao.lambdaUpdate();
        graphWrapper.eq(Graph::getId, this.id);
        if (Objects.nonNull(this.name)) {
            graphWrapper.set(Graph::getName, this.name);
        }
        if (Objects.nonNull(this.description)) {
            graphWrapper.set(Graph::getDescription, this.description);
        }
        graphWrapper.set(Graph::getUpdateTime, new Date());
        graphWrapper.update();
    }

    /**
     * 删除.
     */
    public void remove() {
        GraphDao graphDao = SpringContextHolder.getBean(GraphDao.class);
        graphDao.lambdaUpdate()
                .eq(Graph::getOrganizationId, this.organizationId)
                .eq(Graph::getType, this.type)
                .eq(Graph::getId, this.id)
                .remove();
    }
}
