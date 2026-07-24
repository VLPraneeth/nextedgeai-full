import { InstanceFeatures, useGetFeatureStatusQuery } from 'store/instance-feature/api';

export const useInsightsThoughtspotEnabled = () => {
  const { data: insightsThoughtspotFeature } = useGetFeatureStatusQuery(InstanceFeatures.INSIGHTS_PROVIDER);

  return insightsThoughtspotFeature?.status === 'active';
};
