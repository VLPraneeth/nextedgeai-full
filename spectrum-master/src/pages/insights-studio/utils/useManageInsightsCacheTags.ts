import { useEffect } from 'react';

import { useEnhancedDispatch } from 'hooks/redux';
import { tags } from 'store/api';
import { insightsApiUtil } from 'store/insights-studio/api';
import { InsightsDashboard } from 'store/insights-studio/types';

/**
 * Custom hook to manage rtk-query cache tags for insights
 */
export const useManageInsightsCacheTags = (availableDashboards: InsightsDashboard[], selectedDashboardId: string) => {
  const dispatch = useEnhancedDispatch();

  useEffect(() => {
    // Invalidate the tags of non-selected dashboards
    dispatch(
      insightsApiUtil.invalidateTags(
        availableDashboards
          .filter((dash) => dash.id !== selectedDashboardId)
          .map((dash) => tags.InsightsDashboard(dash.id))
      )
    );
  }, [dispatch, availableDashboards, selectedDashboardId]);

  // Reset the dashboard for when the user returns right away
  useEffect(() => {
    return () => {
      dispatch(insightsApiUtil.invalidateTags([tags.InsightsDashboardList, tags.InsightsDataCardList]));
    };
  }, [dispatch]);
};
