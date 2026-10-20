package com.devin.ontology.domain.vo;

import com.devin.ontology.common.annocation.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;

/**
 * 2025/11/16 16:00:34.
 *
 * <p>
 *  组织项目(Organization)UpdateVO
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Schema(description = "组织项目UpdateVO")
public class OrganizationUpdateVO implements Serializable {
    @Serial
    private static final long serialVersionUID = -49276312709759617L;

    @Schema(description = "项目id")
    private BigInteger id; 
    
    @Schema(description = "项目名称")
    @NotBlank(message = "{required.parameter.error}")
    @Size(max = 100)
    private String name;
    
    @Schema(description = "项目描述")
    @Size(max = 500)
    private String description;
    
}

