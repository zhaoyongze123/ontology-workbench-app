package com.devin.ontology.controller;

import com.devin.ontology.common.utils.r.ApiResult;
import com.devin.ontology.domain.vo.CypherQueryVO;
import com.devin.ontology.query.ReadOnlyCypherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Collection;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 2025/8/8 22:03.
 *
 * <p></p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0
 * @since 1.0
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "cypher")
@RequestMapping("/cypher")
public class CypherController {
    private final ReadOnlyCypherService readOnlyCypherService;

    /**
     * 图谱查询.
     *
     * @param queryVO     cypher查询VO
     * @return 查询结果
     */
    @PostMapping("query")
    @Operation(summary = "只读图谱查询", description = "在权限校验、只读数据库事务和资源上限内执行 Cypher 查询")
    public ApiResult<Collection<Map<String, Object>>> query(
            @Valid @RequestBody final CypherQueryVO queryVO
    ) {
        return ApiResult.success(readOnlyCypherService.query(queryVO));
    }
}
