package com.devin.ontology.common.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 2025/7/14 15:32.
 *
 * <p></p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0
 * @since 1.0
 */
@Data
@Schema(description = "通用查询VO对象")
public class CommonQueryVO {

    @Schema(description = "当前页码")
    private Integer pageNum;

    @Schema(description = "每页数量")
    private Integer pageSize;
}
