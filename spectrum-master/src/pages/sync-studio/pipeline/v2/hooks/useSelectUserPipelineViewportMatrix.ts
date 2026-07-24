import { ReactFlowProps } from '@xyflow/react';
import { useMemo } from 'react';

import { useEnhancedSelector } from 'hooks/redux';
import { selectUserPipelineViewportMatrices } from 'store/entity-pipeline/selectors';

const useSelectUserPipelineViewportMatrix = (
  entityId: string
): Pick<ReactFlowProps, 'defaultViewport' | 'fitView' | 'maxZoom'> => {
  const viewportData = useEnhancedSelector(selectUserPipelineViewportMatrices);

  return useMemo(() => {
    if (viewportData?.[entityId]) {
      // Using the matrix from g6-editor to maintain backwards compatability and
      // keep backend stored data consistent.
      const [zoom, , , , , , x, y] = viewportData?.[entityId];
      return { defaultViewport: { zoom, x, y }, maxZoom: 2 };
    }
    return { fitView: true, maxZoom: 1 };
  }, [entityId, viewportData]);
};

export default useSelectUserPipelineViewportMatrix;
