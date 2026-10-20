package com.devin.ontology.domain.vo;

import com.devin.ontology.common.annocation.Size;
import com.devin.ontology.common.enums.GraphTypeEnum;
import com.devin.ontology.common.enums.TextTypeEnum;
import com.devin.ontology.model.PropertySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.List;

/**
 * 2025/11/16 18:33:02.
 *
 * <p>
 *  节点对象(Node)UpdateVO
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Schema(description = "节点对象UpdateVO")
public class NodeUpdateVO implements Serializable {
    @Serial
    private static final long serialVersionUID = -90791234604727185L;

    @Schema(description = "节点对象id")
    @NotNull(message = "{required.parameter.error}")
    private BigInteger id;

    @Schema(description = "实体节点关联的本体节点对象id")
    private BigInteger linkOntologyId;
    
    @Schema(description = "节点对象名称")
    @Size(max = 63, type = TextTypeEnum.BYTE)
    @NotBlank(message = "{required.parameter.error}")
    private String name;
    
    @Schema(description = "节点对象描述")
    @Size(max = 500)
    private String description;

    @Schema(description = "节点对象属性JSON格式定义")
    @NotNull(message = "{required.parameter.error}")
    private List<PropertySchema> propertySchemaJson;
    
    @Schema(description = "节点对象属性JSON")
    private String propertyJson;
    
    @Schema(description = "图谱id")
    @NotNull(message = "{required.parameter.error}")
    private BigInteger graphId;

    @Schema(description = "图谱类型")
    @NotNull(message = "{required.parameter.error}")
    private GraphTypeEnum graphType;
    
}

