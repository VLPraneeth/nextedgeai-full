import { Edge, OnDelete } from '@xyflow/react';
import { useCallback } from 'react';

import AppConstants from 'utils/AppConstants';

import { ReactFlowNodeV2 } from '../types/ReactFlow.types';
import useEnhancedReactFlow from './useEnhancedReactFlow';

const useOnDelete = (setNodes: React.Dispatch<React.SetStateAction<ReactFlowNodeV2[]>>) => {
  const allNodes = useEnhancedReactFlow().getNodes();
  const allEdges = useEnhancedReactFlow().getEdges();

  const onDelete: OnDelete<ReactFlowNodeV2, Edge> = useCallback(
    ({ nodes, edges }) => {
      const nodesWithSubnodes: string[] = [AppConstants.DECISION_FUNCTION_NAME, AppConstants.SWITCH_FUNCTION_NAME];

      nodes.forEach((node) => {
        // Remove all subnodes when a decision or switch node is deleted
        if (nodesWithSubnodes.includes(node.data.fullNode.apiName || '')) {
          const edgesToDelete = allEdges.filter((edge) => edge.source === node.id);

          const nodesIdsToDelete = allNodes
            .filter((n) => edgesToDelete.map((edge) => edge.target).includes(n.id))
            .map((n) => n.id);

          setNodes((prev) => prev.filter((n) => !nodesIdsToDelete.includes(n.id)));
        }

        const removedEdgeSources = edges.map((edge) => edge.source);
        const removedEdgeTargets = edges.map((edge) => edge.target);
        const removedEdgeSourcesAndTargets = [...removedEdgeSources, ...removedEdgeTargets];

        // Remove subnodes when it's target is deleted
        const orphanedPillNodeIds = allNodes
          .filter((n) => n.type === 'pillNode' && removedEdgeSourcesAndTargets.includes(n.id))
          .map((n) => n.id);

        setNodes((prev) => prev.filter((n) => !orphanedPillNodeIds.includes(n.id)));
      });
    },
    [allEdges, allNodes, setNodes]
  );

  return onDelete;
};

export default useOnDelete;
