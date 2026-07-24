import { Edge, ReactFlowInstance } from '@xyflow/react';
import { useCallback } from 'react';

import AppConstants from 'utils/AppConstants';

import { flowToLegacyEdges, flowToLegacyNodes } from '../PipelineTransformer';
import { ReactFlowNodeV2 } from '../types/ReactFlow.types';

interface SaveablePipelineParams {
  storedPipeline: any;
  reactFlow: ReactFlowInstance<ReactFlowNodeV2, Edge>;
}

export const useSaveablePipeline = () => {
  return useCallback(({ storedPipeline, reactFlow }: SaveablePipelineParams) => {
    const pipeline = reactFlow.toObject();
    const newNodes = flowToLegacyNodes(pipeline.nodes);
    const newEdges = flowToLegacyEdges(pipeline.edges);

    const saveablePipeline = {
      ...storedPipeline,
    };

    if (storedPipeline?.draftStatus === AppConstants.GRAPH_STATUS.NEW) {
      saveablePipeline.nodes = newNodes;
      saveablePipeline.edges = newEdges;
    } else {
      saveablePipeline.draft = { ...storedPipeline.draft, nodes: newNodes, edges: newEdges };
    }

    return saveablePipeline;
  }, []);
};
