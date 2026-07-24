import { useCallback } from 'react';

import { getAsyncNodeConfig } from 'actions/entityPipelineActions';
import { useEnhancedSelector as useSelector, useEnhancedDispatch } from 'hooks/redux';
import { selectSelectedGraphNode } from 'store/pipeline/selectors';
import AppConstants from 'utils/AppConstants';

export const useDynamicConfig = () => {
  const selectedGraphNode = useSelector(selectSelectedGraphNode);
  const dispatch = useEnhancedDispatch();
  const currentGraph = useSelector((state) => state.pipeline.currentGraph);
  const dynamicConfigStatus = useSelector((state) => state.entityPipeline.dynamicConfigStatus);
  const dynamicConfigValues = useSelector((state) => state.entityPipeline.dynamicConfigValues);

  const isLoading = dynamicConfigStatus?.[selectedGraphNode?.id] === AppConstants.FETCH_STATUS.LOADING;
  const dynamicConfig = dynamicConfigValues?.[selectedGraphNode?.id];

  const getDynamicNodeConfig = useCallback(() => {
    if (!dynamicConfig && !isLoading) {
      dispatch(getAsyncNodeConfig(selectedGraphNode?.id, currentGraph));
    }
  }, [currentGraph, dispatch, dynamicConfig, isLoading, selectedGraphNode?.id]);

  return {
    selectedGraphNode,
    currentGraph,
    dynamicConfigStatus,
    dynamicConfigValues,
    isLoading,
    dynamicConfig,
    getDynamicNodeConfig,
    isCoreEntityNode: selectedGraphNode?.nodeType === AppConstants.NODE_TYPE.CORE_ENTITY,
  };
};
