import { sortBy } from 'lodash';
import { useEffect, useMemo } from 'react';

import { EMPTY_ARRAY } from 'store/constants';
import { useLazyGetDashboardsQuery } from 'store/insights-studio/api';
import { InstanceFeatures, useGetFeatureStatusQuery } from 'store/instance-feature/api';

import { ExampleDashboards } from './ExampleDashboards';

/**
 * Custom hook to handle merge and sort of Example and API dashboards
 * in Insights Studio
 */
export const useDashboards = () => {
  const [
    getDashboards,
    {
      data: apiDashboards,
      isLoading: dashListLoading,
      isUninitialized: dashListUninitialized,
      isFetching: dashListFetching,
    },
  ] = useLazyGetDashboardsQuery();
  const { data: insightsFeature, isLoading: insightsFeatureStatusLoading } = useGetFeatureStatusQuery(
    InstanceFeatures.INSIGHTS
  );

  const isInsightsEnabled = insightsFeature?.status === 'active';

  useEffect(() => {
    // Prevent loading data outside insights pages
    if (isInsightsEnabled && window.location.pathname.includes('insights') && !apiDashboards && !dashListLoading) {
      getDashboards();
    }
  }, [isInsightsEnabled, apiDashboards, dashListLoading, getDashboards]);

  return useMemo(() => {
    // Don't return the list until all the required data is loaded
    if (
      dashListLoading ||
      insightsFeatureStatusLoading ||
      // Lazy query reset the initialized state so we check for it as well
      // We also check for fetching for subsequent fetch
      (isInsightsEnabled && (dashListUninitialized || dashListFetching))
    ) {
      return EMPTY_ARRAY;
    }

    // Copy Example dashboards to avoid mutating original
    const list = sortBy(ExampleDashboards, ['displayName']);

    if (isInsightsEnabled) {
      list.unshift.apply(list, sortBy(apiDashboards, ['displayName']));
    }

    return list;
  }, [
    apiDashboards,
    dashListFetching,
    dashListLoading,
    dashListUninitialized,
    insightsFeatureStatusLoading,
    isInsightsEnabled,
  ]);
};
