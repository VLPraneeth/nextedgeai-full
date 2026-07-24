import { Edge, MarkerType, useReactFlow } from '@xyflow/react';
import ObjectID from 'bson-objectid';
import produce from 'immer';
import { find } from 'lodash';
import { createSelector } from 'reselect';

import { PipelineFunction } from 'store/pipeline-functions';
import { GraphStatus, NodeType } from 'store/pipeline/types';
import AppConstants from 'utils/AppConstants';
import { tNamespaced } from 'utils/i18nUtil';

import { getExtraDataForNode, nodeTypeMap } from './PipelineTransformer';
import { PipelineNodeV2 } from './types/BackendPipeline.types';
import { ReactFlowNodeV2 } from './types/ReactFlow.types';

export function isGraphStatusEditable(version: GraphStatus) {
  const editableStatuses: GraphStatus[] = [AppConstants.GRAPH_STATUS.NEW, AppConstants.GRAPH_STATUS.DRAFT];
  return editableStatuses.includes(version);
}

const END_LOOP_OFFSET = { x: 290, y: 350 };
const FOR_EACH_OFFSET = { x: 104, y: 165 };

// Selector to check if a connection is valid
export const validConnectionSelector = (nodes: ReactFlowNodeV2[], edges: Edge[]) => {
  return createSelector(
    (connector: { source: string; target: string }) => connector.source,
    (connector: { source: string; target: string }) => connector.target,
    (source, target) => {
      // Cannot connect to yourself
      if (source === target) {
        return false;
      }

      // Check if both source and target nodes exist
      const sourceNode = nodes.find((node) => node.id === source);
      const targetNode = nodes.find((node) => node.id === target);

      if (!sourceNode || !targetNode) {
        return false; // If either node doesn't exist, return false
      }

      if (targetNode.type === 'pillNode') {
        return false;
      }

      // Check if there is already an edge between the nodes
      const edgeExists = edges.some(
        (edge) =>
          (edge.source === source && edge.target === target) || (edge.source === target && edge.target === source)
      );
      if (edgeExists) {
        return false;
      }

      return true;
    }
  );
};

const getSubnodes = (
  newNode: ReactFlowNodeV2,
  supplementalNodeData: Record<string, any>
): [ReactFlowNodeV2[], Edge[]] => {
  const tn = tNamespaced('PipelineEditor');

  const subNodes = [];
  const subNodeEdges = [];

  if (newNode) {
    const loopFunction = find(supplementalNodeData.pipelineFunctions, { name: AppConstants.LOOP_FUNCTION_NAME });

    if (loopFunction.id === newNode.data.fullNode.configuration.configId) {
      // Add the For Each sub node
      const forEachFunction = find(supplementalNodeData.pipelineFunctions, {
        name: AppConstants.FOR_EACH_FUNCTION_NAME,
      }) as PipelineFunction;

      const forEachNodeId = ObjectID.generate();

      const { x: loopX, y: loopY } = newNode.position;

      // Set the new coordinates to be below the loop node
      const newX = loopX + FOR_EACH_OFFSET.x;
      const newY = loopY + FOR_EACH_OFFSET.y;

      const forEachPosition = { x: newX, y: newY };

      const fullForEachNode: ReactFlowNodeV2['data']['fullNode'] = {
        id: forEachNodeId,
        configuration: {
          configId: forEachFunction.id,
          definition: forEachFunction.id,
          value: undefined, // The For Each node holds no value
        },
        name: tn('for_each'),
        nodeType: AppConstants.NODE_TYPE.FUNCTION,
        location: forEachPosition,
        label: tn('for_each'),
        inputPorts: [
          {
            datatype: 'object',
            maxConnections: 1,
            portType: 'INPUT',
          },
        ],
        outputPorts: [
          {
            datatype: 'object',
            maxConnections: 1,
            portType: 'OUTPUT',
          },
        ],
      };

      const forEachNode: ReactFlowNodeV2 = {
        id: forEachNodeId,
        type: 'pillNode',
        data: {
          extraData: getExtraDataForNode(
            { ...fullForEachNode, configuration: { configId: fullForEachNode.configuration.configId } },
            supplementalNodeData
          ),
          fullNode: fullForEachNode,
        },
        position: forEachPosition,
      };

      subNodes.push(forEachNode);

      const forEachEdge: Edge = {
        id: ObjectID.generate(),
        type: 'customEdge',
        source: newNode.id,
        target: forEachNode.id,
        markerEnd: { type: MarkerType.ArrowClosed },
      };

      subNodeEdges.push(forEachEdge);

      // Add End Loop function
      const endLoopFunction = find(supplementalNodeData.pipelineFunctions, {
        name: AppConstants.END_LOOP_FUNCTION_NAME,
      }) as PipelineFunction;

      const endLoopNodeId = ObjectID.generate();

      const { x: endLoopX, y: endLoopY } = newNode.position;

      // Set the new coordinates to be below the loop node
      const newEndX = endLoopX + END_LOOP_OFFSET.x;
      const newEndY = endLoopY + END_LOOP_OFFSET.y;

      const endLoopPosition = { x: newEndX, y: newEndY };

      const fullEndLoopNode: ReactFlowNodeV2['data']['fullNode'] = {
        id: endLoopNodeId,
        configuration: {
          configId: endLoopFunction.id,
          definition: endLoopFunction.id,
          value: undefined, // The End Loop node holds no value
        },
        name: tn('end_loop'),
        nodeType: AppConstants.NODE_TYPE.FUNCTION,
        location: endLoopPosition,
        label: tn('end_loop'),
        inputPorts: [
          {
            datatype: 'object',
            maxConnections: 1,
            portType: 'INPUT',
          },
        ],
        outputPorts: [
          {
            datatype: 'object',
            maxConnections: 1,
            portType: 'OUTPUT',
          },
        ],
      };

      const endLoopNode: ReactFlowNodeV2 = {
        id: endLoopNodeId,
        type: 'pillNode',
        data: {
          extraData: getExtraDataForNode(
            { ...fullEndLoopNode, configuration: { configId: fullEndLoopNode.configuration.configId } },
            supplementalNodeData
          ),
          fullNode: fullEndLoopNode,
        },
        position: endLoopPosition,
      };

      subNodes.push(endLoopNode);

      const endLoopEdge: Edge = {
        id: ObjectID.generate(),
        type: 'customEdge',
        source: endLoopNode.id,
        target: newNode.id,
        markerEnd: { type: MarkerType.ArrowClosed },
      };

      subNodeEdges.push(endLoopEdge);
    }
  }

  return [subNodes, subNodeEdges];
};

export const useHandleNodeDrop = (
  supplementalNodeData: Record<string, any>,
  setNodes: React.Dispatch<React.SetStateAction<ReactFlowNodeV2[]>>,
  setEdges: React.Dispatch<React.SetStateAction<Edge[]>>
) => {
  const { getViewport, screenToFlowPosition } = useReactFlow();

  return (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();

    const viewport = getViewport();

    try {
      const node = JSON.parse(event.dataTransfer.getData('graph-node'));
      const { displayName, configId } = node;

      const nodeType: NodeType = node.nodeType;

      const nodeWidth = 280; // width of your node (pre-zoom)
      const nodeHeight = 116; // height of your node (pre-zoom)

      const position = screenToFlowPosition({
        x: event.clientX - (nodeWidth / 2) * viewport.zoom,
        y: event.clientY - (nodeHeight / 2) * viewport.zoom,
      });

      const newNodeId = ObjectID.generate();

      const extraData = getExtraDataForNode(
        { ...node, configuration: { configId: node.configId } },
        supplementalNodeData
      );
      const fullNode: PipelineNodeV2 = {
        id: newNodeId,
        configuration: {
          configId,
          definition: configId,
        },
        name: displayName,
        nodeType,
        location: position,
        label: displayName,
        inputPorts: [
          {
            datatype: 'object',
            maxConnections: 1,
            portType: 'INPUT',
          },
        ],
        outputPorts: [
          {
            datatype: 'object',
            maxConnections: 1,
            portType: 'OUTPUT',
          },
        ],
      };

      if ('functionActionApiName' in extraData) {
        fullNode.apiName = extraData.functionActionApiName;
      }

      const newNode: ReactFlowNodeV2 = {
        id: newNodeId,
        type: nodeTypeMap[nodeType] || 'default',
        data: {
          extraData,
          fullNode,
        },
        position,
      };

      const [subNodes, subNodeEdges] = getSubnodes(newNode, supplementalNodeData);

      setNodes((currentNodes) => [...currentNodes, newNode, ...subNodes]);
      setEdges((currentEdges) => [...currentEdges, ...subNodeEdges]);
    } catch (error) {}
  };
};

// How close nodes can be before being considered "stacked"
export const NODE_STACKING_BOUNDARY = 25;

export interface NodeLocation {
  id: string;
  x: number;
  y: number;
}

const nodeIsStacked = (node1: NodeLocation, node2: ReactFlowNodeV2) => {
  if (node1.id === node2.id) {
    return false;
  }
  const x1 = node1.x;
  const x2 = node2.position.x;

  if (x1 >= x2 + NODE_STACKING_BOUNDARY) {
    return false;
  }
  if (x1 <= x2 - NODE_STACKING_BOUNDARY) {
    return false;
  }

  const y1 = node1.y;
  const y2 = node2.position.y;

  if (y1 >= y2 + NODE_STACKING_BOUNDARY) {
    return false;
  }
  if (y1 <= y2 - NODE_STACKING_BOUNDARY) {
    return false;
  }

  return true;
};

export const getUnstackedNodes = (nodes: ReactFlowNodeV2[]): ReactFlowNodeV2[] => {
  return produce(nodes, (draft) => {
    draft.forEach((node) => {
      let stackedNode;

      do {
        // Check if the current node is stacked with another one
        stackedNode = draft.find((node2) =>
          nodeIsStacked({ id: node.id, x: node.position.x, y: node.position.y }, node2)
        );

        if (stackedNode) {
          // Offset the coordinates of the stacked node
          node.position.x = stackedNode.position.x + NODE_STACKING_BOUNDARY;
          node.position.y = stackedNode.position.y + NODE_STACKING_BOUNDARY;
        }
      } while (stackedNode); // Keep trying until no stacked node is found

      // Return the node with updated coordinates if changed, else original
      return node;
    });
  });
};
