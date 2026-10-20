package com.devin.ontology.domain.vo;

import com.devin.ontology.common.vo.CommonDeleteVO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;

/**
 * 2025/11/19 19:22:06.
 *
 * <p>
 *  节点对象关系(Relationship)DeleteVO
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RelationshipDeleteVO extends CommonDeleteVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 791281241587292117L;

    /**
     * 图谱id.
     */
    @Schema(description = "图谱id")
    @NotNull(message = "{required.parameter.error}")
    private BigInteger graphId;
    
    /**
     * 关键词.
     */
    private String keyword;
}

