package com.devin.ontology.domain.vo;

import com.devin.ontology.common.vo.CommonQueryVO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;
import java.io.Serializable;

/**
 * 2025/11/16 16:00:34.
 *
 * <p>
 *  组织项目(Organization)QueryVO
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrganizationQueryVO extends CommonQueryVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 241369025541212466L;
    
    /**
     * 关键词.
     */
    private String keyword;
    
}

