package com.devin.ontology.mapper;

import com.devin.ontology.domain.entity.Graph;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 2025/11/16 00:42:32.
 *
 * <p>
 *  图谱(Graph)Mapper层
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
public interface GraphMapper extends BaseMapper<Graph> {
    
}
    
