import { InstanceFeatures, useGetFeatureStatusQuery } from 'store/instance-feature/api';

export const usePipelineDataQualityEnabled = () => {
  const { data: pipelineDataQuality } = useGetFeatureStatusQuery(InstanceFeatures.DFI_V2);
  return pipelineDataQuality?.status === 'active';
};
