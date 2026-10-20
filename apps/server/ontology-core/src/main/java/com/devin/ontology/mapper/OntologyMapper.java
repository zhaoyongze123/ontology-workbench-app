package com.devin.ontology.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devin.ontology.domain.entity.Ontology;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;

/**
 * 2025/11/16 18:32:59.
 *
 * <p>
 *  本体对象(Ontology)Mapper层
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
@Component("ontologyMapper-v2")
public interface OntologyMapper extends BaseMapper<Ontology> {
    
}
    
