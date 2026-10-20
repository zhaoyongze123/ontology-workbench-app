package com.devin.ontology.controller;

import com.devin.ontology.common.enums.GraphTypeEnum;
import com.devin.ontology.common.utils.r.Addition;
import com.devin.ontology.common.utils.r.ApiResult;
import com.devin.ontology.common.utils.r.PageResult;
import com.devin.ontology.domain.vo.NodeDeleteVO;
import com.devin.ontology.domain.vo.NodeQueryVO;
import com.devin.ontology.domain.vo.NodeSaveVO;
import com.devin.ontology.domain.vo.NodeUpdateVO;
import com.devin.ontology.domain.vo.NodeVO;
import com.devin.ontology.domain.vo.OptionalVO;
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
 * 2025/11/16 18:32:59.
 *
 * <p>
 * 节点对象(Node)Controller层
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "node")
@RestController
@RequiredArgsConstructor
@RequestMapping("/node")
public class NodeController {

    private final GraphStrategyFactory graphStrategyFactory;

    /**
     * 保存节点对象.
     *
     * @param nodeSaveVO 节点对象保存信息
     * @return ApiResult
     */
    @PostMapping("/saveNode")
    @Operation(summary = "保存节点对象")
    public ApiResult<Void> saveNode(
            @RequestBody @Validated final NodeSaveVO nodeSaveVO
    ) {
        GraphStrategy graphStrategy = graphStrategyFactory.getStrategy(nodeSaveVO.getGraphType());
        graphStrategy.saveNode(nodeSaveVO);
        return ApiResult.success();
    }

    /**
     * 修改节点对象.
     *
     * @param nodeUpdateVO 节点对象修改信息
     * @return ApiResult
     */
    @PutMapping("/editNode")
    @Operation(summary = "修改节点对象")
    public ApiResult<Void> editNode(
            @RequestBody @Validated final NodeUpdateVO nodeUpdateVO
    ) {
        GraphStrategy graphStrategy = graphStrategyFactory.getStrategy(nodeUpdateVO.getGraphType());
        graphStrategy.updateNode(nodeUpdateVO);
        return ApiResult.success();
    }

    /**
     * 删除节点对象.
     *
     * @param nodeDeleteVO 节点对象删除信息
     * @return ApiResult
     */
    @PostMapping("/deleteNode")
    @Operation(summary = "删除节点对象")
    public ApiResult<Void> deleteNode(
            @RequestBody @Validated final NodeDeleteVO nodeDeleteVO
    ) {
        GraphStrategy graphStrategy = graphStrategyFactory.getStrategy(nodeDeleteVO.getGraphType());
        graphStrategy.deleteNode(nodeDeleteVO);
        return ApiResult.success();
    }

    /**
     * 分页查询节点对象.
     *
     * @param queryVO            节点查询对象
     * @return ApiResult
     */
    @PostMapping("/page")
    @Operation(summary = "分页查询节点对象")
    public ApiResult<List<NodeVO>> pageNode(
            @RequestBody @Validated final NodeQueryVO queryVO
    ) {
        GraphStrategy graphStrategy = graphStrategyFactory.getStrategy(queryVO.getGraphType());
        PageResult<NodeVO> pageResult = graphStrategy.pageNode(queryVO);
        Addition addition = Addition.of(pageResult);

        return ApiResult.success(pageResult.getRecords(), addition);
    }

    /**
     * 获取节点对象信息.
     *
     * @param nodeId    节点id
     * @param graphType 图谱类型
     * @return ApiResult
     */
    @GetMapping("/getNodeInfo")
    @Operation(summary = "获取节点对象信息")
    public ApiResult<NodeVO> getNodeInfo(
            @Parameter(name = "nodeId", description = "节点id", required = true) @RequestParam(name = "nodeId", required = true) final BigInteger nodeId,
            @Parameter(name = "graphType", description = "图谱类型", required = true) @RequestParam(name = "graphType", required = true) final GraphTypeEnum graphType
    ) {
        GraphStrategy graphStrategy = graphStrategyFactory.getStrategy(graphType);
        return ApiResult.success(graphStrategy.getNodeInfo(nodeId));
    }

    /**
     * 获取节点可选对象.
     *
     * @param graphId   图谱id
     * @param graphType 图谱类型
     * @return ApiResult
     */
    @GetMapping("/optional")
    @Operation(summary = "获取节点可选对象")
    public ApiResult<List<OptionalVO>> getNodeOptional(
            @Parameter(name = "graphId", description = "图谱id", required = true) @RequestParam(name = "graphId", required = true) final BigInteger graphId,
            @Parameter(name = "graphType", description = "图谱类型", required = true) @RequestParam(name = "graphType", required = true) final GraphTypeEnum graphType
    ) {
        GraphStrategy graphStrategy = graphStrategyFactory.getStrategy(graphType);
        return ApiResult.success(graphStrategy.getNodeOptional(graphId));
    }
}
