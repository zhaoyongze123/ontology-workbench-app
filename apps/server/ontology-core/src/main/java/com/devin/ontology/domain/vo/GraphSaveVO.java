package com.devin.ontology.domain.vo;

import java.math.BigInteger;
import com.devin.ontology.common.annocation.Size;
import com.devin.ontology.common.enums.GraphTypeEnum;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 2025/11/16 00:42:36.
 *
 * <p>
 *  图谱(Graph)SaveVO
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Schema(description = "图谱SaveVO")
public class GraphSaveVO implements Serializable {
    @Serial
    private static final long serialVersionUID = -60773667588260443L;
    
    @Schema(description = "图谱名称")
    @Size(max = 100)
    @NotBlank(message = "{required.parameter.error}")
    private String name;
    
    @Schema(description = "图谱描述")
    @Size(max = 500)
    private String description;
    
    @Schema(description = "图谱类型（ONTOLOGY: 本体图谱；ENTITY: 实体图谱）")
    @NotNull(message = "{required.parameter.error}")
    private GraphTypeEnum graphType;
    
    @Schema(description = "实体图谱关联本体图谱id")
    private BigInteger linkGraphId;
    
    @Schema(description = "项目id")
    @NotNull(message = "{required.parameter.error}")
    private BigInteger organizationId;
    
}

