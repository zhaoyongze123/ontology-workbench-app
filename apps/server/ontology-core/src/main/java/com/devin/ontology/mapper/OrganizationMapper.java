package com.devin.ontology.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devin.ontology.domain.entity.Organization;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;

/**
 * 2025/11/16 16:00:31.
 *
 * <p>
 *  组织项目(Organization)Mapper层
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
@Component("organizationMapper-v2")
public interface OrganizationMapper extends BaseMapper<Organization> {
    
}
    
