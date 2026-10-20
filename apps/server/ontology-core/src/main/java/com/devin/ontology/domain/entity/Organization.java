package com.devin.ontology.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.devin.ontology.common.utils.IdGenerator;
import com.devin.ontology.dao.OrganizationDao;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Date;

/**
 * 2025/11/16 16:00:28.
 *
 * <p>
 *  组织项目(Organization)Entity层
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@TableName(value = "og_organization")
public class Organization implements Serializable {
    @Serial
    private static final long serialVersionUID = 208084021597205152L;
    
    /**
     * 项目id.
     */
    @TableId
    private BigInteger id; 

    /**
     * 项目名称.
     */
    @TableField("name")
    private String name;      

    /**
     * 项目描述.
     */
    @TableField("description")
    private String description;      

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
     * @return Organization
     */
    public Organization save() {
        OrganizationDao organizationDao = SpringContextHolder.getBean(OrganizationDao.class);
        setId(IdGenerator.generateKey());
        init();
        organizationDao.save(this);
        return this;
    }

    /**
     * 更新.
     */
    public void update() {
        OrganizationDao organizationDao = SpringContextHolder.getBean(OrganizationDao.class);
        setUpdateTime(new Date());
        organizationDao.updateById(this);
    }
}
