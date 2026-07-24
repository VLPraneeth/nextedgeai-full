import { useMemo } from 'react';

import { useEnhancedSelector } from 'hooks/redux';
import { useGetPipelinesDetailsQuery } from 'store/pipeline/api';

import './PipelineDetailsFilterPanel.scss';
import { makeSourceAndDestinationOptions } from './PipelineDetailsFilterPanel.utils';

export const usePipelineDetailsFilterPanel = () => {
  const { data } = useGetPipelinesDetailsQuery();
  const { connectors } = useEnhancedSelector((state) => state.connector);

  const sourceOptions = useMemo(() => makeSourceAndDestinationOptions(data, connectors, 'sources'), [connectors, data]);
  const destinationOptions = useMemo(() => makeSourceAndDestinationOptions(data, connectors, 'sinks'), [
    connectors,
    data,
  ]);

  return {
    sourceOptions,
    destinationOptions,
  };
};
