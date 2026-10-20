package com.devin.ontology.domain.vo;

import java.math.BigInteger;
import java.util.Date;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 2025/11/16 00:42:36.
 *
 * <p>
 *  图谱(Graph)VO
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Schema(description = "图谱VO")
public class GraphVO implements Serializable {
    @Serial
    private static final long serialVersionUID = -17249805673061566L;
    
    @Schema(description = "图谱id")
    private BigInteger id;
    
    @Schema(description = "图谱名称")
    private String name;
    
    @Schema(description = "图谱描述")
    private String description;
    
    @Schema(description = "图谱类型（ONTOLOGY: 本体图谱；ENTITY: 实体图谱）")
    private String type;
    
    @Schema(description = "实体图谱关联本体图谱id")
    private BigInteger linkGraphId;

    @Schema(description = "实体图谱关联本体图谱名称")
    private String linkGraphName;

    @Schema(description = "是否含有实体图谱")
    private Boolean hasEntity;
    
    @Schema(description = "项目id")
    private BigInteger organizationId;
    
    @Schema(description = "创建时间")
    private Date createTime;
    
    @Schema(description = "更新时间")
    private Date updateTime;
    
}

