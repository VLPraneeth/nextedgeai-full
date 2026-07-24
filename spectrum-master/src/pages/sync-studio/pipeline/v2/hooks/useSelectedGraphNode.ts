import { useLocation } from '@reach/router';
import { isEqual } from 'lodash';
import { useEffect, useState } from 'react';

import { EMPTY_ARRAY } from 'store/constants';

import useEnhancedReactFlow from './useEnhancedReactFlow';

export const getNodeIdsFromSearch = (search: string) => {
  const searchParams = new URLSearchParams(search);
  return searchParams.get('nodeIds')?.split(',') || EMPTY_ARRAY;
};

export const useSelectedNodeIds = () => {
  const location = useLocation();

  const [nodeIds, setNodeIds] = useState<string[]>(() => {
    return getNodeIdsFromSearch(location.search);
  });

  // update the search params when a nodeId is selected
  useEffect(() => {
    // Check if the selectedNodeIds have changed.
    const newlySelectedNodeIds = getNodeIdsFromSearch(location.search);

    const nodeIdsHaveChanged = !isEqual([...nodeIds].sort(), [...newlySelectedNodeIds].sort());

    if (nodeIdsHaveChanged) {
      setNodeIds(newlySelectedNodeIds);
    }
  }, [location.search, nodeIds]);

  return { nodeIds, setNodeIds };
};

export const useSelectedGraphNode = () => {
  const { getNode } = useEnhancedReactFlow();

  const { nodeIds, setNodeIds } = useSelectedNodeIds();

  if (nodeIds.length !== 1) {
    return { selectedNode: null, setNodeIds };
  }

  return { selectedNode: getNode(nodeIds[0]) || null, setNodeIds };
};
