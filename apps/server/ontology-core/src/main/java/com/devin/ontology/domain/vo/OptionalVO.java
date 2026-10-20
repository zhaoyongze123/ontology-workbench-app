package com.devin.ontology.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigInteger;

/**
 * 2025/11/22 13:59.
 *
 * <p></p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Schema(description = "可选下拉列表VO")
public class OptionalVO {

    @Schema(description = "可选项ID")
    private BigInteger id;

    @Schema(description = "可选项名称")
    private String name;

    @Schema(description = "可选项描述")
    private String description;
}
