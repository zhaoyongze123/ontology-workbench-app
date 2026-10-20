package com.devin.ontology.common.utils.r;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 2025/6/12 21:39.
 *
 * <p></p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0
 * @since 1.0
 */
@Data
@Schema(description = "附加信息")
public class Addition {

    @Schema(description = "总数量")
    private int total;

    @Schema(description = "当前页")
    private int pageNum;

    @Schema(description = "总页数")
    private int pageSize;

    /**
     * 附加属性.
     *
     * @param page 分页参数
     * @return 附加属性
     */
    public static Addition of(final IPage<?> page) {
        Addition addition = new Addition();
        addition.setTotal((int) page.getTotal());
        addition.setPageNum((int) page.getCurrent());
        addition.setPageSize((int) page.getSize());
        return addition;
    }

    /**
     * 附加属性.
     *
     * @param page 分页参数
     * @return 附加属性
     */
    public static Addition of(final PageResult<?> page) {
        Addition addition = new Addition();
        addition.setTotal(page.getTotal());
        addition.setPageNum(page.getPageNum());
        addition.setPageSize(page.getPageSize());
        return addition;
    }
}
