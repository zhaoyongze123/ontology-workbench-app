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
 * 2025/11/16 18:33:02.
 *
 * <p>
 *  节点对象(Node)VO
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Schema(description = "节点对象VO")
public class NodeVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 865721337924764676L;
    
    @Schema(description = "节点对象id")
    private BigInteger id;

    @Schema(description = "实体对象关联的本体对象id")
    private BigInteger linkOntologyId;

    @Schema(description = "实体对象关联的本体对象名称")
    private String linkOntologyName;
    
    @Schema(description = "节点对象名称")
    private String name;
    
    @Schema(description = "节点对象描述")
    private String description;

    @Schema(description = "节点对象属性JSON格式定义")
    private List<PropertySchema> propertySchemaJson;
    
    @Schema(description = "节点对象属性JSON")
    private String propertyJson;
    
    @Schema(description = "图谱id")
    private BigInteger graphId;

    @Schema(description = "图谱类型")
    private GraphTypeEnum graphType;
    
    @Schema(description = "创建时间")
    private Date createTime;
    
    @Schema(description = "更新时间")
    private Date updateTime;
    
}

