import { InstanceFeatures, useGetFeatureStatusQuery } from 'store/instance-feature/api';

export const useBrandingEnabled = () => {
  const { data: branding } = useGetFeatureStatusQuery(InstanceFeatures.BRAND);
  return branding?.status === 'active';
};
