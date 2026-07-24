import { BaseEdge, ConnectionLineComponentProps, getSmoothStepPath } from '@xyflow/react';

import { ReactFlowNodeV2 } from '../types/ReactFlow.types';
import getClosestSide from './utils/getClosestSide';

const CustomConnectionLine = ({
  fromNode,
  toNode,
  toX,
  toY,
  connectionStatus,
}: ConnectionLineComponentProps<ReactFlowNodeV2>) => {
  const sourceNode = {
    ...fromNode.position,
    width: fromNode.measured.width || 50,
    height: fromNode.measured.height || 50,
    type: fromNode?.type,
  };

  const canConnect = connectionStatus === 'valid';

  const targetNode = canConnect
    ? {
        x: toNode?.position?.x || toX,
        y: toNode?.position?.y || toY,
        width: toNode?.measured?.width || 1,
        height: toNode?.measured?.height || 1,
        type: toNode?.type,
      }
    : {
        x: toX,
        y: toY,
        width: 1,
        height: 1,
      };

  const smoothStepProps = getClosestSide(sourceNode, targetNode);

  const [edgePath] = getSmoothStepPath(smoothStepProps);

  return (
    <>
      <defs>
        <marker
          className="react-flow__arrowhead"
          id="blueArrow"
          markerWidth="12.5"
          markerHeight="12.5"
          viewBox="-10 -10 20 20"
          markerUnits="strokeWidth"
          orient="auto-start-reverse"
          refX="0"
          refY="0">
          <polyline
            points="-5,-4 0,0 -5,4 -5,-4"
            style={{
              stroke: '#3F91F7',
              fill: '#3F91F7',
              strokeWidth: 1,
            }}
          />
        </marker>
        <marker
          id="drag_node"
          markerWidth="120"
          markerHeight="24"
          viewBox="0 0 120 24"
          markerUnits="strokeWidth"
          refX="60"
          refY="12">
          <svg width="120" height="24" viewBox="0 0 120 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect
              x="0.5"
              y="0.5"
              width="119"
              height="23"
              rx="3.5"
              fill="#D9D9D9"
              fill-opacity="0.3"
              stroke="#3F91F7"
              stroke-dasharray="2 2"
            />
          </svg>
        </marker>

        <marker
          id="drag_handle"
          markerWidth="10"
          markerHeight="10"
          viewBox="0 0 10 10"
          markerUnits="strokeWidth"
          orient="auto-start-reverse"
          refX="4"
          refY="4">
          <svg width="10" height="10" viewBox="0 0 10 10" fill="none" xmlns="http://www.w3.org/2000/svg">
            <circle cx="5" cy="5" r="4" fill="white" stroke="#3F91F7" />
          </svg>
        </marker>
      </defs>
      <BaseEdge
        path={edgePath}
        style={{
          stroke: '#3F91F7',
          strokeWidth: 2,
        }}
        markerEnd={canConnect ? `url('#blueArrow')` : `url('#drag_handle')`}
      />
    </>
  );
};

export default CustomConnectionLine;
