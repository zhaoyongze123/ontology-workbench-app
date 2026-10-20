package com.devin.ontology.model;

import com.devin.ontology.common.enums.PropertyTypeEnum;
import com.devin.ontology.common.enums.RequiredEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 2025/11/16 22:02.
 *
 * <p>
 *     属性模式
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Schema(description = "属性模式")
public class PropertySchema {

    @Schema(description = "属性名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "属性类型", requiredMode = Schema.RequiredMode.REQUIRED)
    private PropertyTypeEnum type;

    @Schema(description = "属性描述", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String description;

    @Schema(description = "属性是否必填", requiredMode = Schema.RequiredMode.REQUIRED)
    private RequiredEnum required;

}
