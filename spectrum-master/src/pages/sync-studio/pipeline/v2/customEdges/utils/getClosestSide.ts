import { GetSmoothStepPathParams, Position } from '@xyflow/react';

import { ReactFlowNodeTypes } from '../../types/ReactFlow.types';

export interface SideNodeData {
  x: number;
  y: number;
  width: number;
  height: number;
  type?: ReactFlowNodeTypes;
}

const EDGE_OFFSET = 4;
// This function find the four possible connection points between two nodes and
// then finds the two closest points to connect to. It returns both the
// coordinates and the positions to be passed into `getSmoothStepPath` from
// React Flow.
const getSideCoordinates = (node: SideNodeData) => {
  const sideYOffset = node.type === 'pillNode' ? 0 : 20;

  return {
    top: { x: node.x + node.width / 2, y: node.y - 8 },
    bottom: { x: node.x + node.width / 2, y: node.y + node.height + EDGE_OFFSET }, // Added EDGE_OFFSET to the y-coordinate for bottom side
    left: { x: node.x - EDGE_OFFSET, y: node.y + node.height / 2 + sideYOffset }, // Subtracted EDGE_OFFSET from x for left side, added 20 to y
    right: { x: node.x + node.width + EDGE_OFFSET, y: node.y + node.height / 2 + sideYOffset }, // Added EDGE_OFFSET to x for right side, added 20 to y
  };
};

// Helper function to calculate the distance between two points
const calculateDistance = (point1: { x: number; y: number }, point2: { x: number; y: number }) =>
  Math.hypot(point2.x - point1.x, point2.y - point1.y);

// The main function to get the closest sides along with coordinates, formatted for GetSmoothStepPathParams
const getClosestSide = (sourceNode: SideNodeData, targetNode: SideNodeData): GetSmoothStepPathParams => {
  // Get the middle points of all sides for both source and target nodes
  const sourceSides = getSideCoordinates(sourceNode);
  const targetSides = getSideCoordinates(targetNode);

  let closestSourcePosition = Position.Top; // Default value
  let closestTargetPosition = Position.Top; // Default value
  let minDistance = Infinity; // Initialize minimum distance to a high value

  // Compare each side of the sourceNode with each side of the targetNode
  for (const sourceSide in sourceSides) {
    for (const targetSide in targetSides) {
      const sourcePoint = sourceSides[sourceSide as keyof typeof sourceSides];
      const targetPoint = targetSides[targetSide as keyof typeof targetSides];
      const distance = calculateDistance(sourcePoint, targetPoint);

      // Update closest positions if a new minimum distance is found
      if (distance < minDistance) {
        minDistance = distance;
        closestSourcePosition = sourceSide as Position;
        closestTargetPosition = targetSide as Position;
      }
    }
  }

  // Get the center coordinates of the selected sides
  const closestSourcePoint = sourceSides[closestSourcePosition];
  const closestTargetPoint = targetSides[closestTargetPosition];

  // Return the result in the shape of GetSmoothStepPathParams
  return {
    sourcePosition: closestSourcePosition,
    targetPosition: closestTargetPosition,
    sourceX: closestSourcePoint.x,
    sourceY: closestSourcePoint.y,
    targetX: closestTargetPoint.x,
    targetY: closestTargetPoint.y,
  };
};

export default getClosestSide;
