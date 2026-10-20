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
 * 2025/11/16 18:33:02.
 *
 * <p>
 *  节点删除对象(Node)VO
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Schema(description = "节点删除对象VO")
@EqualsAndHashCode(callSuper = true)
public class NodeDeleteVO extends CommonDeleteVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 865721337924764676L;

    @Schema(description = "图谱id")
    @NotNull(message = "{required.parameter.error}")
    private BigInteger graphId;
    
}

