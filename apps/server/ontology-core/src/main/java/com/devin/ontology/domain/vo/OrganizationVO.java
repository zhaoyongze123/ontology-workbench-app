package com.devin.ontology.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Date;

/**
 * 2025/11/16 16:00:33.
 *
 * <p>
 *  组织项目(Organization)VO
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Schema(description = "组织项目VO")
public class OrganizationVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 523830409882023782L;
    
    @Schema(description = "项目id")
    private BigInteger id;
    
    @Schema(description = "项目名称")
    private String name;
    
    @Schema(description = "项目描述")
    private String description;

    @Schema(description = "本体图谱数量")
    private Long ontologyGraphTotal;

    @Schema(description = "实体图谱数量")
    private Long entityGraphTotal;
    
    @Schema(description = "创建时间")
    private Date createTime;
    
    @Schema(description = "更新时间")
    private Date updateTime;
    
}

