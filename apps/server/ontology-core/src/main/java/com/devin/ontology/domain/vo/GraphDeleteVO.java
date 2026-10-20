package com.devin.ontology.domain.vo;

import com.devin.ontology.common.vo.CommonDeleteVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;

/**
 * 2025/11/16 00:42:37.
 *
 * <p>
 *  图谱(Graph)DeleteVO
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GraphDeleteVO extends CommonDeleteVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 433755700540157791L;

    /**
     * 项目id.
     */
    @Schema(description = "项目id")
    private BigInteger organizationId;
    
    /**
     * 关键词.
     */
    private String keyword;
    
}

