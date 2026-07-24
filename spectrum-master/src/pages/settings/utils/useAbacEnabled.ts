import { InstanceFeatures, useGetFeatureStatusQuery } from 'store/instance-feature/api';

export const useAbacEnabled = () => {
  const { data: abac } = useGetFeatureStatusQuery(InstanceFeatures.ABAC);
  return abac?.status === 'active';
};
