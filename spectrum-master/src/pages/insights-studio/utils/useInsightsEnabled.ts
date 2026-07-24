import { InstanceFeatures, useGetFeatureStatusQuery } from 'store/instance-feature/api';

export const useInsightsEnabled = () => {
  const { data: insightsFeature } = useGetFeatureStatusQuery(InstanceFeatures.INSIGHTS);

  return insightsFeature?.status === 'active';
};
