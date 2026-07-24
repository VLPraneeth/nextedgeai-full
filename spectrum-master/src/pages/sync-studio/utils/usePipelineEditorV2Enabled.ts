import { InstanceFeatures, useGetFeatureStatusQuery } from 'store/instance-feature/api';

export const usePipelineEditorV2Enabled = () => {
  const { data: pipelineEditorV2 } = useGetFeatureStatusQuery(InstanceFeatures.PIPELINE_EDITOR_V2);
  return pipelineEditorV2?.status === 'active';
};
