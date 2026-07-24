import { navigate, useMatch } from '@reach/router';
import { useCallback } from 'react';

import { UsedByType } from 'store/insights-studio/types';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

export const useUnifiedDataCardNavigate = () => {
  const dashboardMatch = useMatch('/insights-studio/:dashboardId/*');
  const dashboardVersionMatch = useMatch('/insights-studio/:dashboardId/:version');
  const dashboardVersionMatchAll = useMatch('/insights-studio/:dashboardId/:version/*');
  const dataCardMatch = useMatch('/insights-studio/:dashboardId/:version/datacard/:dataCardId');
  const datasetMatch = useMatch('/insights-studio/:dashboardId/:version/dataset/:datasetId');
  const thoughtspotDatasetMatch = useMatch('/insights-studio/ts/datasets/:datasetId');
  const thoughtspotPreviewDatasetMatch = useMatch('/insights-studio/ts/datasets/:datasetId/preview');
  const thoughtspotCopyDatasetMatch = useMatch('/insights-studio/ts/datasets/:datasetId/copy');
  const copyDataCardMatch = useMatch('/insights-studio/:dashboardId/:version/datacard/:dataCardId/copy');
  const copyAddDataCardMatch = useMatch('/insights-studio/:dashboardId/:version/datacard/:dataCardId/copy-add');
  const copyDatasetMatch = useMatch('/insights-studio/:dashboardId/:version/dataset/:datasetId/copy');
  const previewDatasetMatch = useMatch('/insights-studio/:dashboardId/:version/dataset/:datasetId/preview');
  const aiAssistedMatch = useMatch('/insights-studio/:dashboardId/:version/aiassisted');
  const dashboardShareWithMatch = useMatch('/insights-studio/:dashboardId/:version/share-with');
  const dashboardSharingDetailsMatch = useMatch('/insights-studio/:dashboardId/:version/sharing-details');
  const sharedDashboardMatch = useMatch('/insightssharing/user/:encodedEmail/dashboard/:dashboardId');

  const getCurrentDashboard = useCallback(
    () => ({
      dashboardId:
        dashboardVersionMatch?.dashboardId ||
        dataCardMatch?.dashboardId ||
        datasetMatch?.dashboardId ||
        copyDataCardMatch?.dashboardId ||
        copyAddDataCardMatch?.dashboardId ||
        copyDatasetMatch?.dashboardId ||
        previewDatasetMatch?.dashboardId ||
        dashboardMatch?.dashboardId ||
        dashboardShareWithMatch?.dashboardId ||
        dashboardSharingDetailsMatch?.dashboardId ||
        sharedDashboardMatch?.dashboardId,
      draft: dashboardVersionMatchAll?.version === 'draft',
    }),
    [
      dashboardVersionMatchAll?.version,
      copyDataCardMatch?.dashboardId,
      copyAddDataCardMatch?.dashboardId,
      copyDatasetMatch?.dashboardId,
      dashboardMatch?.dashboardId,
      dashboardVersionMatch?.dashboardId,
      dataCardMatch?.dashboardId,
      datasetMatch?.dashboardId,
      previewDatasetMatch?.dashboardId,
      dashboardShareWithMatch?.dashboardId,
      dashboardSharingDetailsMatch?.dashboardId,
      sharedDashboardMatch?.dashboardId,
    ]
  );

  const getCurrentDashboardUrl = useCallback(() => {
    const { dashboardId, draft } = getCurrentDashboard();
    if (!dashboardId) {
      return;
    }
    if (draft) {
      return makeUrl(RouteConstants.INSIGHTS_STUDIO_DASHBOARD_DRAFT, { dashboardId });
    } else {
      return makeUrl(RouteConstants.INSIGHTS_STUDIO_DASHBOARD, { dashboardId });
    }
  }, [getCurrentDashboard]);

  const navigateToCurrentDashboard = useCallback(() => {
    const dashboardUrl = getCurrentDashboardUrl();
    if (dashboardUrl) {
      navigate(dashboardUrl);
    }
  }, [getCurrentDashboardUrl]);

  const navigateTo = useCallback(
    (type: UsedByType, id: string, params: Record<string, string> = {}) => {
      const { dashboardId } = getCurrentDashboard();
      // Allow ThoughtSpot dataset routes without dashboard context
      if (type === 'THOUGHT_SPOT_DATASET') {
        return navigate(makeUrl(RouteConstants.INSIGHTS_STUDIO_TS_DATASET, { datasetId: id }, params));
      }
      // Only allow other navigation types from insights (require dashboard)
      if (!dashboardId) {
        return;
      }
      if (type === 'DATACARD') {
        return navigate(makeUrl(RouteConstants.INSIGHTS_STUDIO_DATA_CARD, { dashboardId, dataCardId: id }, params));
      } else if (type === 'DATASET') {
        return navigate(makeUrl(RouteConstants.INSIGHTS_STUDIO_DATASET, { dashboardId, datasetId: id }, params));
      } else if (type === 'AIASSISTED') {
        return navigate(makeUrl(RouteConstants.INSIGHTS_STUDIO_AIASSISTED, { dashboardId }, params));
      }
      throw new Error(`navigate to type ${type} is not supported`);
    },
    [getCurrentDashboard]
  );

  const isDatasetCurrentUrl = useCallback(
    () =>
      // Important to use the current location because useEffect triggered with old useMatch value
      Boolean(window.location.pathname.toLowerCase()?.includes('draft/dataset')),
    []
  );

  const isThoughtspotDatasetCurrentUrl = useCallback(
    () =>
      // Important to use the current location because useEffect triggered with old useMatch value
      Boolean(window.location.pathname.toLowerCase()?.includes('insights-studio/ts/datasets')),
    []
  );

  const isDataCardCurrentUrl = useCallback(
    () =>
      // Important to use the current location because useEffect triggered with old useMatch value
      Boolean(window.location.pathname.toLowerCase()?.includes('draft/datacard')),
    []
  );

  return {
    navigateToCurrentDashboard,
    dashboardMatch,
    dashboardVersionMatch,
    dataCardMatch,
    datasetMatch,
    thoughtspotDatasetMatch,
    thoughtspotCopyDatasetMatch,
    thoughtspotPreviewDatasetMatch,
    copyDataCardMatch,
    copyAddDataCardMatch,
    copyDatasetMatch,
    previewDatasetMatch,
    aiAssistedMatch,
    dashboardSharingDetailsMatch,
    sharedDashboardMatch,
    dashboardShareWithMatch,
    navigateTo,
    isDataCardCurrentUrl,
    isDatasetCurrentUrl,
    isThoughtspotDatasetCurrentUrl,
    getCurrentDashboard,
  };
};
