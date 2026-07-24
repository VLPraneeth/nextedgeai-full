import { useCallback, useMemo } from 'react';

import { ValuesOf } from 'utils/TypeUtils';

import { useGetFeaturesQuery } from './api';

export const InstanceFeature = {};

export type InstanceFeatureName = ValuesOf<typeof InstanceFeature>;

export const useInstanceFeatures = () => {
  const { data: features, isLoading, isFetching, refetch } = useGetFeaturesQuery();

  const visibleFeatures = useMemo(() => features?.filter((feature) => !Boolean(feature.hidden)), [features]);

  const hasVisibleFeatures = useMemo(() => Boolean(visibleFeatures?.length), [visibleFeatures?.length]);

  const isFeatureEnabled = useCallback(
    (featureName: InstanceFeatureName) => Boolean(features?.find((feature) => feature.name === featureName)?.enabled),
    [features]
  );

  return {
    hasVisibleFeatures,
    visibleFeatures,
    features,
    refetch,
    isLoading,
    isFetching,
    isFeatureEnabled,
  };
};
