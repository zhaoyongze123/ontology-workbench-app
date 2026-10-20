package com.devin.ontology.controller;

import com.devin.ontology.common.enums.GraphTypeEnum;
import com.devin.ontology.common.utils.r.Addition;
import com.devin.ontology.common.utils.r.ApiResult;
import com.devin.ontology.common.utils.r.PageResult;
import com.devin.ontology.domain.vo.OptionalVO;
import com.devin.ontology.domain.vo.RelationshipDeleteVO;
import com.devin.ontology.domain.vo.RelationshipQueryVO;
import com.devin.ontology.domain.vo.RelationshipUpdateVO;
import com.devin.ontology.domain.vo.RelationshipSaveVO;
import com.devin.ontology.domain.vo.RelationshipVO;
import com.devin.ontology.strategy.graph.GraphStrategy;
import com.devin.ontology.strategy.graph.GraphStrategyFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigInteger;
import java.util.List;

/**
 * 2025/11/19 19:22:05.
 *
 * <p>
 * 节点对象关系(Relationship)Controller层
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "relationship")
@RestController
@RequiredArgsConstructor
@RequestMapping("/relationship")
public class RelationshipController {

    private final GraphStrategyFactory graphStrategyFactory;

    /**
     * 保存关系.
     *
     * @param relationshipSaveVO 关系保存信息
     * @return ApiResult
     */
    @PostMapping("/save")
    @Operation(summary = "保存关系")
    public ApiResult<RelationshipVO> saveRelationship(
            @RequestBody @Validated final RelationshipSaveVO relationshipSaveVO
    ) {
        GraphStrategy graphStrategy = graphStrategyFactory.getStrategy(relationshipSaveVO.getGraphType());
        graphStrategy.saveRelationship(relationshipSaveVO);
        return ApiResult.success();
    }

    /**
     * 修改关系.
     *
     * @param relationshipUpdateVO 关系修改信息
     * @return ApiResult
     */
    @PutMapping("/edit")
    @Operation(summary = "修改关系")
    public ApiResult<Void> editRelationship(
            @RequestBody @Validated final RelationshipUpdateVO relationshipUpdateVO
    ) {
        GraphStrategy graphStrategy = graphStrategyFactory.getStrategy(relationshipUpdateVO.getGraphType());
        graphStrategy.updateRelationship(relationshipUpdateVO);
        return ApiResult.success();
    }

    /**
     * 分页查询关系.
     *
     * @param queryVO 查询参数
     * @return ApiResult
     */
    @PostMapping("/page")
    @Operation(summary = "分页查询关系")
    public ApiResult<List<RelationshipVO>> pageRelationship(
            @RequestBody @Validated final RelationshipQueryVO queryVO
    ) {
        GraphStrategy graphStrategy = graphStrategyFactory.getStrategy(queryVO.getGraphType());
        PageResult<RelationshipVO> pageResult = graphStrategy.pageRelationship(queryVO);

        Addition addition = Addition.of(pageResult);
        return ApiResult.success(pageResult.getRecords(), addition);
    }

    /**
     * 删除关系.
     *
     * @param relationshipDelete 删除信息
     * @return ApiResult
     */
    @PostMapping("/delete")
    @Operation(summary = "删除节点关系对象")
    public ApiResult<Void> deleteRelationship(
            @RequestBody @Validated final RelationshipDeleteVO relationshipDelete
    ) {
        GraphStrategy graphStrategy = graphStrategyFactory.getStrategy(relationshipDelete.getGraphType());
        graphStrategy.deleteRelationship(relationshipDelete);
        return ApiResult.success();
    }

    /**
     * 获取关系信息.
     *
     * @param graphType      图谱类型
     * @param relationshipId 关系id
     * @return ApiResult
     */
    @GetMapping("/info")
    @Operation(summary = "获取关系信息")
    public ApiResult<RelationshipVO> getRelationshipInfo(
            @Parameter(name = "graphType", description = "图谱类型", required = true) @RequestParam(name = "graphType", required = true) final GraphTypeEnum graphType,
            @Parameter(name = "relationshipId", description = "关系id", required = true) @RequestParam(name = "relationshipId", required = true) final BigInteger relationshipId
    ) {
        GraphStrategy graphStrategy = graphStrategyFactory.getStrategy(graphType);
        return ApiResult.success(graphStrategy.getRelationshipInfo(relationshipId));
    }

    /**
     * 获取关联的本体关系列表.
     *
     * @param sourceNodeId 源节点id
     * @param targetNodeId 目标节点id
     * @return 列表
     */
    @GetMapping("/link/getOntologyRelationshipList")
    @Operation(summary = "获取关联的本体关系列表")
    public ApiResult<List<OptionalVO>> getLinkOntologyRelationshipList(
            @Parameter(name = "sourceNodeId", description = "源节点id", required = true) @RequestParam(name = "sourceNodeId", required = true) final BigInteger sourceNodeId,
            @Parameter(name = "targetNodeId", description = "目标节点id", required = true) @RequestParam(name = "targetNodeId", required = true) final BigInteger targetNodeId
    ) {
        List<OptionalVO> relationshipVOList = graphStrategyFactory.getLinkOntologyRelationshipList(sourceNodeId, targetNodeId);
        return ApiResult.success(relationshipVOList);
    }

    /**
     * 获取关系可选对象.
     *
     * @param graphId   图谱id
     * @param graphType 图谱类型
     * @return 列表
     */
    @GetMapping("/optional")
    @Operation(summary = "获取关系可选对象")
    public ApiResult<List<OptionalVO>> getRelationshipOptional(
            final BigInteger graphId,
            final GraphTypeEnum graphType
    ) {
        GraphStrategy graphStrategy = graphStrategyFactory.getStrategy(graphType);
        return ApiResult.success(graphStrategy.getRelationshipOptional(graphId));
    }
}
