package com.devin.ontology.domain.vo;

import com.devin.ontology.common.enums.GraphTypeEnum;
import com.devin.ontology.common.vo.CommonQueryVO;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import lombok.EqualsAndHashCode;

/**
 * 2025/11/16 00:42:37.
 *
 * <p>
 *  图谱(Graph)QueryVO
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GraphQueryVO extends CommonQueryVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 433755700540157791L;

    /**
     * 项目id.
     */
    private BigInteger organizationId;

    /**
     * 图谱类型.
     */
    private GraphTypeEnum graphType;
    
    /**
     * 关键词.
     */
    private String keyword;
    
}

