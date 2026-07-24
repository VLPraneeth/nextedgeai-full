import { OnSelectionChangeParams, useOnSelectionChange } from '@xyflow/react';
import { useCallback } from 'react';

import { useEnhancedDispatch } from 'hooks/redux';
import { setSelectedNodeIds } from 'store/pipeline/actions';

import { useUpdateSelectedNodeIdsQueryParam } from '../PipelineEditor.hooks';
import { usePipelineEditor } from './context/PipelineEditorV2.context';

const ReactFlowHandlers = ({ setSelectedGraphNode }: { setSelectedGraphNode: any }) => {
  const { setSelectedNodeIds: setSelectedNodeIdsV2 } = usePipelineEditor();
  const updateSelectedNodeIdsQueryParam = useUpdateSelectedNodeIdsQueryParam();

  const dispatch = useEnhancedDispatch();

  const handleSelectionChange = useCallback(
    ({ nodes }: OnSelectionChangeParams) => {
      const nodeIds = nodes.map(({ id }) => id);
      updateSelectedNodeIdsQueryParam(nodeIds);
      dispatch(setSelectedNodeIds(nodeIds));
      setSelectedNodeIdsV2(nodeIds);

      if (nodes.length === 1) {
        setSelectedGraphNode(nodes[0].data.fullNode);
      }
      if (nodes.length === 0) {
        setSelectedGraphNode(null);
      }
    },
    [dispatch, setSelectedGraphNode, updateSelectedNodeIdsQueryParam, setSelectedNodeIdsV2]
  );

  useOnSelectionChange({
    onChange: handleSelectionChange,
  });

  return null;
};

export default ReactFlowHandlers;
