package com.devin.ontology.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devin.ontology.domain.entity.Entity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 2025/11/19 23:17:59.
 *
 * <p>
 *  实体对象(Entity)Mapper层
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
public interface EntityMapper extends BaseMapper<Entity> {
    
}
    
