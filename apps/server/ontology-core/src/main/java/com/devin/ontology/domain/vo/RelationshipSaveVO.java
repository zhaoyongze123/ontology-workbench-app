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
 * 2025/11/19 19:22:05.
 *
 * <p>
 *  节点对象关系(Relationship)SaveVO
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Schema(description = "节点对象关系SaveVO")
public class RelationshipSaveVO implements Serializable {
    @Serial
    private static final long serialVersionUID = -74988075386211314L;

    @Schema(description = "实体关系关联本体关系id")
    private BigInteger linkOntologyRelationshipId;
    
    @Schema(description = "源节点id")
    @NotNull(message = "{required.parameter.error}")
    private BigInteger sourceNodeId;
    
    @Schema(description = "目标节点id")
    @NotNull(message = "{required.parameter.error}")
    private BigInteger targetNodeId;

    @Schema(description = "图谱id")
    @NotNull(message = "{required.parameter.error}")
    private BigInteger graphId;
    
    @Schema(description = "关系名称")
    @NotBlank(message = "{required.parameter.error}")
    @Size(max = 63, type = TextTypeEnum.BYTE)
    private String name;
    
    @Schema(description = "关系描述")
    @Size(max = 500)
    private String description;
    
    @Schema(description = "本体关系属性Schema（实体关系属性JSON格式）")
    @NotNull(message = "{required.parameter.error}")
    private List<PropertySchema> propertySchemaJson;

    @Schema(description = "实体关系属性Json")
    private String propertyJson;

    @Schema(description = "图谱类型")
    @NotNull(message = "{required.parameter.error}")
    private GraphTypeEnum graphType;
}

