import { NodeMouseHandler } from '@xyflow/react';
import { createContext, ReactNode, useCallback, useContext, useMemo, useState } from 'react';

import { nodeIsEditiblePill } from '../customNodes/PillNode';
import useEnhancedReactFlow from '../hooks/useEnhancedReactFlow';
import { getUnstackedNodes } from '../PipelineEditorV2.utils';
import { ReactFlowNodeV2 } from '../types/ReactFlow.types';

interface PillDropdownContextType {
  dropdownVisibleNodeId: string;
  setDropdownVisibleNodeId: (nodeId: string) => void;
}

const PillDropdownContext = createContext<PillDropdownContextType>({
  dropdownVisibleNodeId: '',
  setDropdownVisibleNodeId: () => {},
});

export const PillDropdownProvider = ({ children }: { children: ReactNode }) => {
  const [dropdownVisibleNodeId, setDropdownVisibleNodeId] = useState('');

  const value = useMemo(
    () => ({
      dropdownVisibleNodeId,
      setDropdownVisibleNodeId,
    }),
    [dropdownVisibleNodeId, setDropdownVisibleNodeId]
  );

  return <PillDropdownContext.Provider value={value}>{children}</PillDropdownContext.Provider>;
};

export const usePillDropdownContext = () => useContext(PillDropdownContext);

export const useOnNodeClick = () => {
  const { dropdownVisibleNodeId, setDropdownVisibleNodeId } = usePillDropdownContext();

  const fn: NodeMouseHandler<ReactFlowNodeV2> = useCallback(
    (event, node) => {
      if (nodeIsEditiblePill(node)) {
        setDropdownVisibleNodeId(dropdownVisibleNodeId === node.id ? '' : node.id);
      } else {
        setDropdownVisibleNodeId('');
      }
    },
    [dropdownVisibleNodeId, setDropdownVisibleNodeId]
  );

  return fn;
};

export const useOnNodeDoubleClick = () => {
  const { setDropdownVisibleNodeId } = usePillDropdownContext();

  const fn: NodeMouseHandler<ReactFlowNodeV2> = useCallback(() => {
    setDropdownVisibleNodeId('');
  }, [setDropdownVisibleNodeId]);

  return fn;
};

export const useUnstackNodes = () => {
  const { setNodes } = useEnhancedReactFlow();

  return useCallback(() => {
    setNodes((currentNodes) => getUnstackedNodes(currentNodes));
  }, [setNodes]);
};
