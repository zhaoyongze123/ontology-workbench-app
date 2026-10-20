package com.devin.ontology.domain.vo;

import com.devin.ontology.common.enums.GraphTypeEnum;
import com.devin.ontology.common.vo.CommonQueryVO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.List;

/**
 * 2025/11/19 19:22:06.
 *
 * <p>
 *  节点对象关系(Relationship)QueryVO
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RelationshipQueryVO extends CommonQueryVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 791281241587292117L;

    /**
     * 图谱类型.
     */
    @Schema(description = "图谱类型")
    @NotNull(message = "{required.parameter.error}")
    private GraphTypeEnum graphType;

    /**
     * 图谱id.
     */
    @Schema(description = "图谱id")
    @NotNull(message = "{required.parameter.error}")
    private BigInteger graphId;
    
    /**
     * 关键词.
     */
    @Schema(description = "关键词")
    private String keyword;

    /**
     * 关系idList.
     */
    @Schema(description = "关系idList")
    private List<BigInteger> relationshipIdList;
    
}

