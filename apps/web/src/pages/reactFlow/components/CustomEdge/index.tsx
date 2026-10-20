import { BaseEdge, EdgeLabelRenderer, getStraightPath } from '@xyflow/react';
import React from 'react';
import styles from './index.less';

interface CustomEdgeProps {
  data?: {
    label?: string;
    offset?: number;
    totalEdges?: number;
    edgeIndex?: number;
  };
}

const CustomEdge: React.FC<{
  id: string;
  source: string;
  target: string;
  sourceX: number;
  sourceY: number;
  targetX: number;
  targetY: number;
  data?: any;
  style?: any;
  markerEnd?: any;
  markerStart?: any;
}> = ({
  id,
  source,
  target,
  sourceX,
  sourceY,
  targetX,
  targetY,
  data,
  style,
  markerEnd,
  markerStart,
}) => {
  // 计算边的偏移量，避免重叠
  const offset = data?.offset || 0;
  const totalEdges = data?.totalEdges || 1;
  const edgeIndex = data?.edgeIndex || 0;
  // 节点半径
  const nodeRadius = 46;
  // 动态计算偏移距离，根据连线数量调整
  const baseOffsetDistance = 12;
  const maxOffsetDistance = 20;
  const offsetDistance = Math.min(
    baseOffsetDistance + (totalEdges - 1) * 2,
    maxOffsetDistance,
  );

  /** 计算偏移后的起点和终点 */
  const getOffsetPoint = (
    srcX: number,
    srcY: number,
    tgtX: number,
    tgtY: number,
    offset: number,
  ) => {
    /** 计算基础几何信息 */
    const dx = tgtX - srcX;
    const dy = tgtY - srcY;
    const angle = Math.atan2(dy, dx);
    /** 计算偏移索引中心化 (使得连线对称分布) */
    const effectiveOffset = totalEdges > 1 ? offset - (totalEdges - 1) / 2 : 0;
    /** 确定偏移方向和距离 */
    const perpAngle = angle + Math.PI / 2;
    /** 规范化方向：保证 A->B 和 B->A 的偏移方向一致（始终都在连线的一侧） */
    const isReversed = srcX > tgtX || (srcX === tgtX && srcY > tgtY);
    const direction = isReversed ? -1 : 1;
    /** 计算总偏移距离（考虑方向和索引中心化） */
    const totalOffsetDistance = direction * effectiveOffset * offsetDistance;
    /** 计算偏移向量 */
    const offsetX = Math.cos(perpAngle) * totalOffsetDistance;
    const offsetY = Math.sin(perpAngle) * totalOffsetDistance;
    /** 计算节点边缘截断距离 (从圆心到连线与圆周交点的距离) 利用勾股定理：R^2 = d^2 + L^2 => L = sqrt(R^2 - d^2) */
    let edgeDistance = nodeRadius;
    if (Math.abs(totalOffsetDistance) < nodeRadius) {
      edgeDistance = Math.sqrt(
        nodeRadius * nodeRadius - totalOffsetDistance * totalOffsetDistance,
      );
    }
    /** 计算边缘截断向量 */
    const edgeDx = Math.cos(angle) * edgeDistance;
    const edgeDy = Math.sin(angle) * edgeDistance;
    /** 计算最终坐标：(原坐标 + 偏移向量) ± 边缘截断向量 */
    return {
      source: {
        x: srcX + offsetX + edgeDx,
        y: srcY + offsetY + edgeDy,
      },
      target: {
        x: tgtX + offsetX - edgeDx,
        y: tgtY + offsetY - edgeDy,
      },
    };
  };
  let edgePath = '';
  let labelX = 0;
  let labelY = 0;
  let textRotation = 0;
  /** 检查是否为自环边 */
  if (source === target) {
    /** 自环边 (Self-loop) 路径计算 */
    const loopOffset = (data?.edgeIndex || 0) * 15;
    const loopWidth = 66 + loopOffset; // 向左延伸宽度
    const verticalStretch = 60; // 垂直拉伸力度，控制曲线平滑度
    /** 关键点坐标 */
    const topY = sourceY - nodeRadius;
    const bottomY = sourceY + nodeRadius;
    const leftX = sourceX - loopWidth;
    /** 控制点垂直坐标 */
    const ctrlTopY = topY - verticalStretch;
    const ctrlLeftTopY = sourceY - verticalStretch;
    const ctrlLeftBottomY = sourceY + verticalStretch;
    const ctrlBottomY = bottomY + verticalStretch;
    /** 绘制三次贝塞尔曲线: Top -> Left -> Bottom */
    edgePath = `M ${sourceX} ${topY} C ${sourceX} ${ctrlTopY}, ${leftX} ${ctrlLeftTopY}, ${leftX} ${sourceY} C ${leftX} ${ctrlLeftBottomY}, ${sourceX} ${ctrlBottomY}, ${sourceX} ${bottomY}`;
    labelX = leftX;
    labelY = sourceY;
    textRotation = 0;
  } else {
    /** 常规边 (Straight Edge) 路径计算 */
    const { source: offsetSource, target: offsetTarget } = getOffsetPoint(
      sourceX,
      sourceY,
      targetX,
      targetY,
      offset,
    );
    const [path, lx, ly] = getStraightPath({
      sourceX: offsetSource.x,
      sourceY: offsetSource.y,
      targetX: offsetTarget.x,
      targetY: offsetTarget.y,
    });
    edgePath = path;
    labelX = lx;
    labelY = ly;
    /** 计算文字角度，确保在 -90 到 90 度之间以保持可读性 */
    const degrees =
      Math.atan2(targetY - sourceY, targetX - sourceX) * (180 / Math.PI);
    textRotation = degrees > 90 || degrees < -90 ? degrees + 180 : degrees;
  }
  /** 根据连线数量调整线条样式 */
  const getEdgeStyle = () => {
    const baseStyle = { ...style };
    /** 当有多条连线时，调整线条粗细和透明度 */
    if (totalEdges > 1) {
      baseStyle.strokeWidth =
        (style?.strokeWidth || 1.5) - (totalEdges - 1) * 0.1;
      baseStyle.opacity = Math.max(0.7, 1 - (totalEdges - 1) * 0.05);
    }
    return baseStyle;
  };

  return (
    <>
      <BaseEdge
        id={id}
        path={edgePath}
        style={getEdgeStyle()}
        markerEnd={markerEnd}
        markerStart={markerStart}
      />
      {data?.label && (
        <EdgeLabelRenderer>
          <div
            className={styles.edgeLabel}
            style={{
              transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px) rotate(${textRotation}deg)`,
              // maxWidth: `${Math.max(
              //   80,
              //   Math.min(
              //     240,
              //     Math.hypot(targetX - sourceX, targetY - sourceY) - 30,
              //   ),
              // )}px`,
            }}
            title={data.label}
          >
            {data.label}
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  );
};

export default React.memo(CustomEdge);
