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
 * 2025/11/16 18:33:02.
 *
 * <p>
 *  节点对象(Node)QueryVO
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Schema(description = "节点对象(Node)QueryVO")
@EqualsAndHashCode(callSuper = true)
public class NodeQueryVO extends CommonQueryVO implements Serializable {
    @Serial
    private static final long serialVersionUID = -72720276481052302L;

    /**
     * 图谱id.
     */
    @Schema(description = "图谱id")
    @NotNull(message = "{required.parameter.error}")
    private BigInteger graphId;

    /**
     * 图谱类型.
     */
    @Schema(description = "图谱类型")
    @NotNull(message = "{required.parameter.error}")
    private GraphTypeEnum graphType;
    
    /**
     * 关键词.
     */
    @Schema(description = "关键词")
    private String keyword;

    /**
     * 节点id列表.
     */
    @Schema(description = "节点id列表")
    private List<BigInteger> nodeIdList;

    /**
     * 关联本体节点id列表.
     */
    @Schema(description = "关联本体节点id列表")
    private List<BigInteger> linkOntologyNodeIdList;
    
}

