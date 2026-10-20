package com.devin.ontology.domain.vo;

import com.devin.ontology.common.enums.GraphTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigInteger;

/**
 * 2025/9/2 18:44.
 *
 * <p></p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0
 * @since 1.0
 */
@Data
@Schema(description = "Cypher查询参数")
public class CypherQueryVO {

    @NotNull
    @Schema(description = "Cypher语句")
    private String cypher;

    @NotNull
    @Schema(description = "图谱id")
    private BigInteger graphId;

    @Schema(description = "图谱类型")
    @NotNull
    private GraphTypeEnum graphType;
}
