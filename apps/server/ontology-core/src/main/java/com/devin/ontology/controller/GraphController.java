package com.devin.ontology.controller;

import com.devin.ontology.common.enums.GraphTypeEnum;
import com.devin.ontology.common.utils.ExportUtils;
import com.devin.ontology.common.utils.r.Addition;
import com.devin.ontology.common.utils.r.ApiResult;
import com.devin.ontology.common.utils.r.PageResult;
import com.devin.ontology.domain.vo.GraphQueryVO;
import com.devin.ontology.domain.vo.GraphSaveVO;
import com.devin.ontology.domain.vo.GraphUpdateVO;
import com.devin.ontology.domain.vo.GraphVO;
import com.devin.ontology.domain.vo.NodeVO;
import com.devin.ontology.model.JsonGraphInfo;
import com.devin.ontology.strategy.graph.GraphStrategy;
import com.devin.ontology.strategy.graph.GraphStrategyFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

/**
 * 2025/11/16 00:42:33.
 *
 * <p>
 * 图谱(Graph)Controller层
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "graph")
@RestController
@RequiredArgsConstructor
@RequestMapping("/graph")
public class GraphController {

    private final GraphStrategyFactory graphStrategyFactory;

    /**
     * 保存图谱.
     *
     * @param graphSaveVO 图谱保存VO
     * @return ApiResult
     */
    @PostMapping("save")
    @Operation(summary = "保存图谱")
    public ApiResult<GraphVO> saveGraph(
            @RequestBody @Validated final GraphSaveVO graphSaveVO
    ) {
        GraphStrategy strategy = graphStrategyFactory.getStrategy(graphSaveVO.getGraphType());
        return ApiResult.success(strategy.saveGraph(graphSaveVO));
    }

    /**
     * 修改图谱.
     *
     * @param graphUpdateVO 图谱修改VO
     * @return ApiResult
     */
    @PutMapping("edit")
    @Operation(summary = "修改图谱")
    public ApiResult<Void> editGraph(
            @RequestBody @Validated final GraphUpdateVO graphUpdateVO
    ) {
        GraphStrategy strategy = graphStrategyFactory.getStrategy(graphUpdateVO.getGraphType());
        strategy.updateGraph(graphUpdateVO);
        return ApiResult.success();
    }

    /**
     * 分页查询图谱.
     *
     * @param pageNum        页码
     * @param pageSize       每页数量
     * @param graphType      图谱类型
     * @param organizationId 组织ID
     * @return 分页结果
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询图谱")
    public ApiResult<List<GraphVO>> pageGraph(
            @Parameter(name = "pageNum", description = "当前页码", required = false) @RequestParam(name = "pageNum", required = false) final Integer pageNum,
            @Parameter(name = "pageSize", description = "每页数量", required = false) @RequestParam(name = "pageSize", required = false) final Integer pageSize,
            @Parameter(name = "graphType", description = "图谱类型", required = true) @RequestParam(name = "graphType", required = true) final GraphTypeEnum graphType,
            @Parameter(name = "organizationId", description = "项目id", required = true) @RequestParam(name = "organizationId", required = true) final BigInteger organizationId
    ) {
        GraphQueryVO queryVO = new GraphQueryVO();
        queryVO.setPageNum(pageNum);
        queryVO.setPageSize(pageSize);
        queryVO.setGraphType(graphType);
        queryVO.setOrganizationId(organizationId);

        GraphStrategy graphStrategy = graphStrategyFactory.getStrategy(graphType);
        PageResult<GraphVO> pageResult = graphStrategy.pageGraph(queryVO);

        Addition addition = Addition.of(pageResult);
        return ApiResult.success(pageResult.getRecords(), addition);
    }

    /**
     * 删除图谱.
     *
     * @param graphId        图谱id
     * @param graphType      图谱类型
     * @param organizationId 组织id
     * @return 删除结果
     */
    @DeleteMapping("/delete")
    @Operation(summary = "删除图谱")
    public ApiResult<Void> deleteGraph(
            @Parameter(name = "graphId", description = "图谱id", required = true) @RequestParam(name = "graphId", required = true) final BigInteger graphId,
            @Parameter(name = "graphType", description = "图谱类型", required = true) @RequestParam(name = "graphType", required = true) final GraphTypeEnum graphType,
            @Parameter(name = "organizationId", description = "项目id", required = true) @RequestParam(name = "organizationId", required = true) final BigInteger organizationId
    ) {
        GraphVO graphVO = new GraphVO();
        graphVO.setId(graphId);
        graphVO.setType(graphType.name());
        graphVO.setOrganizationId(organizationId);

        GraphStrategy graphStrategy = graphStrategyFactory.getStrategy(graphType);
        graphStrategy.deleteGraph(graphVO);
        return ApiResult.success();
    }

    /**
     * 获取图谱信息.
     *
     * @param graphId   图谱id
     * @param graphType 图谱类型
     * @return 图谱信息
     */
    @GetMapping("/getInfo")
    @Operation(summary = "获取图谱信息")
    public ApiResult<GraphVO> getGraphInfo(
            @Parameter(name = "graphId", description = "图谱id", required = true) @RequestParam(name = "graphId", required = true) final BigInteger graphId,
            @Parameter(name = "graphType", description = "图谱类型", required = true) @RequestParam(name = "graphType", required = true) final GraphTypeEnum graphType
    ) {
        GraphStrategy graphStrategy = graphStrategyFactory.getStrategy(graphType);
        return ApiResult.success(graphStrategy.getGraphInfo(graphId));
    }

    /**
     * 获取实体图谱关联的本体图谱的节点列表.
     *
     * @param entityGraphId 实体图谱id
     * @return 本体图谱节点列表
     */
    @GetMapping("/link/getOntologyNodeList")
    @Operation(summary = "获取实体图谱关联的本体图谱的节点列表")
    public ApiResult<List<NodeVO>> getLinkOntologyNodeList(
            @Parameter(name = "entityGraphId", description = "实体图谱id", required = true) @RequestParam(name = "entityGraphId", required = true) final BigInteger entityGraphId
    ) {
        List<NodeVO> nodeVOList = graphStrategyFactory.getLinkOntologyNodeList(entityGraphId);
        return ApiResult.success(nodeVOList);
    }

    /**
     * 导出图谱.
     *
     * @param graphId   图谱id
     * @param graphType 图谱类型
     * @return ApiResult
     */
    @GetMapping("/export/json")
    @Operation(summary = "导出图谱JSON")
    public ApiResult<JsonGraphInfo> exportGraph(
            @Parameter(name = "graphId", description = "图谱id", required = true) @RequestParam(name = "graphId", required = true) final BigInteger graphId,
            @Parameter(name = "graphType", description = "图谱类型", required = true) @RequestParam(name = "graphType", required = true) final GraphTypeEnum graphType
    ) {
        GraphStrategy graphStrategy = graphStrategyFactory.getStrategy(graphType);
        return ApiResult.success(graphStrategy.exportGraph(graphId));
    }

    /**
     * 导入图谱.
     *
     * @param file 文件
     * @param organizationId 项目id
     * @param graphType 图谱类型
     * @return ApiResult
     */
    @PostMapping(value = "/import/json", headers = "content-type=multipart/form-data")
    @Operation(summary = "导入图谱JSON")
    public ApiResult<Void> importGraph(
            @Parameter(name = "file", description = "文件", required = true) @RequestPart("file") final MultipartFile file,
            @Parameter(name = "organizationId", description = "项目id", required = true) @RequestPart("organizationId") final String organizationId,
            @Parameter(name = "graphType", description = "图谱类型", required = true) @RequestPart("graphType") final String graphType
    ) throws IOException {
        GraphStrategy graphStrategy = graphStrategyFactory.getStrategy(GraphTypeEnum.valueOf(graphType));
        String jsonData = ExportUtils.importData(file);
        graphStrategy.importGraph(jsonData, new BigInteger(organizationId));
        return ApiResult.success();
    }
}
