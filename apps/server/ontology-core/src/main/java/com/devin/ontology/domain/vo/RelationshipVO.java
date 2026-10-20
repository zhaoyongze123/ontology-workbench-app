package com.devin.ontology.domain.vo;

import com.devin.ontology.common.enums.GraphTypeEnum;
import com.devin.ontology.model.PropertySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Date;
import java.util.List;

/**
 * 2025/11/19 19:22:05.
 *
 * <p>
 *  节点对象关系(Relationship)VO
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Schema(description = "节点对象关系VO")
public class RelationshipVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 946622642468861569L;
    
    @Schema(description = "关系id")
    private BigInteger id;
    
    @Schema(description = "源节点id")
    private BigInteger sourceNodeId;

    @Schema(description = "源节点名称")
    private String sourceNodeName;
    
    @Schema(description = "目标节点id")
    private BigInteger targetNodeId;

    @Schema(description = "目标节点名称")
    private String targetNodeName;
    
    @Schema(description = "关系名称")
    private String name;
    
    @Schema(description = "关系描述")
    private String description;

    @Schema(description = "实体关系关联本体id")
    private BigInteger linkOntologyId;

    @Schema(description = "实体关系关联本体名称")
    private String linkOntologyName;
    
    @Schema(description = "关系属性Schema（本体关系属性JSON格式）")
    private List<PropertySchema> propertySchemaJson;

    @Schema(description = "实体关系Json")
    private String propertyJson;

    @Schema(description = "图谱类型")
    private GraphTypeEnum graphType;
    
    @Schema(description = "创建时间")
    private Date createTime;
    
    @Schema(description = "更新时间")
    private Date updateTime;
    
}

