import { BaseEdge, EdgeProps, getStraightPath, useInternalNode } from '@xyflow/react';

import { getEdgeParams } from './utils';

function FloatingEdge({ source, target, ...props }: EdgeProps) {
  const sourceNode = useInternalNode(source);
  const targetNode = useInternalNode(target);

  if (!sourceNode || !targetNode) {
    return null;
  }

  const { sx, sy, tx, ty } = getEdgeParams(sourceNode, targetNode);

  const [edgePath] = getStraightPath({
    sourceX: sourceNode.position.x + (sourceNode.internals.handleBounds?.source?.[0].x || 0) + 16,
    sourceY: sourceNode.position.y + (sourceNode.internals.handleBounds?.source?.[0].y || 0) + 16,
    targetX: tx,
    targetY: ty,
  });

  console.log('render edge');

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
            stroke-linecap="round"
            stroke-linejoin="round"
            points="-5,-4 0,0 -5,4 -5,-4"
            style={{
              stroke: '#2c8ff2',
              fill: '#2c8ff2',
              strokeWidth: 1,
            }}
          />
        </marker>
      </defs>
      <BaseEdge
        path={edgePath}
        {...props}
        markerEnd={props.selected ? `url('#blueArrow')` : `url('#1__type=arrowclosed')`}
      />
    </>
  );
}

export default FloatingEdge;
