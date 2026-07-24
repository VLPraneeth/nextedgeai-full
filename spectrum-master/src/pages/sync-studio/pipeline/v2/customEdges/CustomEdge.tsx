import { BaseEdge, EdgeProps, getSmoothStepPath, useInternalNode } from '@xyflow/react';

import { ReactFlowNodeV2 } from '../types/ReactFlow.types';
import getClosestSide, { SideNodeData } from './utils/getClosestSide';

const CustomEdge = ({ id, source, target, ...props }: EdgeProps) => {
  const existingSourceNode = useInternalNode<ReactFlowNodeV2>(source);
  const existingTargetNode = useInternalNode<ReactFlowNodeV2>(target);

  if (!existingSourceNode || !existingTargetNode) {
    return null;
  }

  const sourceNode: SideNodeData = {
    ...existingSourceNode.position,
    width: existingSourceNode.measured.width || 50,
    height: existingSourceNode.measured.height || 50,
    type: existingSourceNode.type,
  };
  const targetNode: SideNodeData = {
    ...existingTargetNode.position,
    width: existingTargetNode.measured.width || 50,
    height: existingTargetNode.measured.height || 50,
    type: existingTargetNode.type,
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
      </defs>
      <BaseEdge
        {...props}
        path={edgePath}
        style={{
          stroke: props.selected ? '#3F91F7' : '#b1b1b7',
          strokeWidth: props.selected ? 2 : 2,
        }}
        markerEnd={props.selected ? `url('#blueArrow')` : `url('#1__type=arrowclosed')`}
      />
    </>
  );
};

export default CustomEdge;
