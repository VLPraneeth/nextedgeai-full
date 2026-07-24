import { useReactFlow } from '@xyflow/react';

import { ReactFlowNodeV2 } from '../types/ReactFlow.types';

// The useReactFlow hook with our custom node/edge types as generics
const useEnhancedReactFlow = () => {
  return useReactFlow<ReactFlowNodeV2>();
};

export default useEnhancedReactFlow;
