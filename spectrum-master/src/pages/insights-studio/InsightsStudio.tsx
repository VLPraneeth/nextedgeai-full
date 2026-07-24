import { Link, RouteComponentProps, useMatch } from '@reach/router';
import { Alert, Button, message, Modal } from 'antd';
import { isEqual } from 'lodash';
import { useEffect, useState } from 'react';

import Can from 'components/Can';
import RouteSpin from 'components/RouteSpin';
import { ScrollableArea } from 'components/scrollable-area/ScrollableArea';
import { Text } from 'components/typography';
import { useForbiddenRedirect } from 'hooks/useForbiddenRedirect';
import { useGetDataStoreQuery, useLazyGetDataStoreLagQuery } from 'store/datastore/api';
import {
  useEnableInsightsMutation,
  useGetLastVisitedDashboardQuery,
  useSetLastVisitedDashboardMutation,
} from 'store/insights-studio';
import { InsightsDashboard } from 'store/insights-studio/types';
import { InstanceFeatures, useGetFeatureStatusQuery } from 'store/instance-feature/api';
import { navigateTo } from 'utils/AppUtil';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';

import { AiAssistedPanel } from './ai-assisted/AiAssistedPanel';
import { AuthoringSidebar } from './components/authoring-sidebar/AuthoringSidebar';
import { DashboardLayout } from './components/dashboard-layout/DashboardLayout';
import { DataCardWizard } from './components/data-card-wizard/DataCardWizard';
import { ExampleDashHeader } from './components/example-dash-header/ExampleDashHeader';
import { InsightsToolbar } from './components/insights-toolbar/InsightsToolbar';
import { UnifiedDataCardWizard } from './components/unified-data-card-wizard/UnifiedDataCardWizard';
import { DataCardAuthoringContextProvider } from './context/DataCardAuthoringContext';
import { DatasetAuthoringContextProvider } from './context/DatasetAuthoringContext';
import { InsightsSidebarContextProvider } from './context/InsightsSidebarContext';
import { useInsightsViewContext } from './context/InsightsViewContext';
import { UnifiedDataCardAuthoringContextProvider } from './context/UnifiedDataCardAuthoringContext';
import CopyModal from './copy/CopyModal';
import { ShareWithModal } from './dashboard-sharing/ShareWithModal';
import { SharingDetails } from './dashboard-sharing/SharingDetailsModal';
import DatasetWizard from './dataset/DatasetWizard';
import PreviewModal from './dataset/preview/PreviewModal';
import { DataCardSettingsContextProvider, Settings } from './settings';
import ConfigureDashboardVariableModal from './settings/ConfigureDashboardVariableModal';
import DashboardVariableSettingsModal from './settings/DashboardVariableSettingsModal';
import InsightsThoughtspotMainPage from './thoughtspot/InsightsThoughtspotMainPage';
import { useDashboards, useManageInsightsCacheTags } from './utils';
import { useUnifiedDataCardNavigate } from './utils/useUnifiedDataCardNavigate';

import './InsightsStudio.scss';

const tn = tNamespaced('InsightsStudio');

/**
 * Root page for Insights Studio.
 * Renders for route /insights-studio/*
 */
const InsightsStudio = ({ uri, location }: RouteComponentProps) => {
  const dashboardWithVersionMatch = useMatch('/insights-studio/:dashboardId/:version/*');
  const dashboardMatch = useMatch('/insights-studio/:dashboardId/*');
  const insightsHomeMatch = useMatch('/insights-studio');

  const [enableConfirmationVisible, setEnableConfirmationVisible] = useState(false);

  const urlMatch = dashboardMatch || dashboardWithVersionMatch;
  const { getCurrentDashboard } = useUnifiedDataCardNavigate();

  const Error403 = useForbiddenRedirect({
    studioPermissions: [AllPermissions.READ_INSIGHTS],
  });

  const { data: insightsFeature, isFetching: insightsFeatureFetching } = useGetFeatureStatusQuery(
    InstanceFeatures.INSIGHTS
  );

  const { isThoughtSpotView } = useInsightsViewContext();

  const availableDashboards = useDashboards();
  const { data: lastVisitedDashboard, isFetching } = useGetLastVisitedDashboardQuery();
  const [setLastVisitedDashboard] = useSetLastVisitedDashboardMutation();
  const [enableInsights, { isLoading: enableInsightsLoading }] = useEnableInsightsMutation();
  const { data: dataStore } = useGetDataStoreQuery();
  const [getDataStoreLags, { data: lags, error: lagsError }] = useLazyGetDataStoreLagQuery();

  const [selectedDashboard, setSelectedDashboard] = useState<InsightsDashboard>();

  useManageInsightsCacheTags(availableDashboards, selectedDashboard?.id ?? '');

  useEffect(() => {
    if (dataStore) {
      getDataStoreLags();
    }
  }, [getDataStoreLags, dataStore]);

  useEffect(() => {
    if (availableDashboards.length === 0 || isFetching || isThoughtSpotView) {
      // Wait for list to be populated before taking any action
      return;
    }

    // Only trigger on dashboard navigations
    if (!urlMatch && !insightsHomeMatch) {
      return;
    }

    // Find dashboard from URL in list
    const { dashboardId } = getCurrentDashboard();
    const dashFromURL = availableDashboards.find((dash) => dash.id === dashboardId);
    if (dashFromURL) {
      const useNestedDraft = Boolean(dashboardWithVersionMatch?.version === 'draft' && !!dashFromURL.draft);
      const dashToSelect = useNestedDraft ? dashFromURL.draft : dashFromURL;
      const newLastVisited = { lastVisitedDashboardId: dashFromURL.id, useNestedDraft };

      if (dashToSelect && !isEqual(selectedDashboard, dashToSelect)) {
        setSelectedDashboard(dashToSelect);
        if (
          // Don't set last visited to sample dash, the request will error
          dashToSelect.id !== 'sampleDash1' &&
          !isEqual(lastVisitedDashboard, newLastVisited)
        ) {
          setLastVisitedDashboard(newLastVisited);
        }
      }
      return;
    }

    if (
      lastVisitedDashboard &&
      availableDashboards.find((dash) => dash.id === lastVisitedDashboard.lastVisitedDashboardId) // Checks if last visited dashboard is still available
    ) {
      // load last visited dashboard
      navigateTo(
        RouteConstants.INSIGHTS_STUDIO +
          '/' +
          lastVisitedDashboard.lastVisitedDashboardId +
          (lastVisitedDashboard.useNestedDraft ? '/draft' : '')
      );
    } else {
      navigateTo(RouteConstants.INSIGHTS_STUDIO + '/' + availableDashboards[0].id);
    }
  }, [
    availableDashboards,
    availableDashboards?.length,
    lastVisitedDashboard,
    selectedDashboard,
    setLastVisitedDashboard,
    isFetching,
    dashboardWithVersionMatch?.version,
    getCurrentDashboard,
    urlMatch,
    insightsHomeMatch,
    isThoughtSpotView,
  ]);

  if (!selectedDashboard && !isThoughtSpotView) {
    return Error403 ?? <RouteSpin />;
  }

  const isDraft = Boolean(selectedDashboard?.draftStatus === 'NEW');

  const hasLag = !!lags?.filter((lag) => lag.pendingRecords).length;

  return (
    Error403 ?? (
      <DataCardSettingsContextProvider>
        <InsightsSidebarContextProvider>
          <DataCardAuthoringContextProvider>
            <DatasetAuthoringContextProvider>
              <UnifiedDataCardAuthoringContextProvider>
                <div className="insights-studio">
                  <div className="insights-studio__main">
                    {insightsFeature?.status !== 'active' &&
                      insightsFeature?.status !== 'activating' &&
                      !insightsFeatureFetching && (
                        <div className="insights-studio__enable-feature">
                          <span>{tn('enable_insights_description')}</span>
                          <Can permission={AllPermissions.ENABLE_INSIGHTS}>
                            <Button
                              type="primary"
                              loading={enableInsightsLoading}
                              onClick={() => setEnableConfirmationVisible(true)}>
                              {tn('enable_insights')}
                            </Button>
                          </Can>
                        </div>
                      )}
                    {insightsFeature?.status === 'activating' && !isThoughtSpotView && (
                      <div className="insights-studio__enable-feature-loading">
                        <div>{tn('enable_insights_activating')}</div>
                      </div>
                    )}
                    {insightsFeature?.status === 'active' && !lagsError && hasLag && !isThoughtSpotView && (
                      <div className="insights-studio__loading-alert">
                        <Alert
                          message={
                            <div>
                              {tn('enable_insights_loading_description')}{' '}
                              <Link to={RouteConstants.DATA_STUDIO_ROOT}>{tn('data_studio')}</Link>{' '}
                            </div>
                          }
                          type="info"
                          showIcon
                        />
                      </div>
                    )}
                    {isThoughtSpotView ? (
                      <>
                        <InsightsThoughtspotMainPage uri={`${uri}/ts`} location={location} />
                      </>
                    ) : (
                      selectedDashboard && (
                        <>
                          <InsightsToolbar
                            availableDashboards={availableDashboards}
                            selectedDashboard={selectedDashboard}
                          />
                          {selectedDashboard.isExample ? <ExampleDashHeader /> : null}
                          <ScrollableArea>
                            <DashboardLayout dashboardId={selectedDashboard.id} key={selectedDashboard.id} />
                          </ScrollableArea>
                        </>
                      )
                    )}
                  </div>
                  {insightsFeature?.status === 'active' && isDraft && !isThoughtSpotView ? <AuthoringSidebar /> : null}
                </div>
                {(insightsFeature?.status === 'active' || isThoughtSpotView) && (
                  <>
                    <Settings />
                    <ConfigureDashboardVariableModal />
                    <DashboardVariableSettingsModal />
                    <DataCardWizard />
                    <DatasetWizard />
                    <UnifiedDataCardWizard />
                    <CopyModal />
                    <PreviewModal />
                    <AiAssistedPanel />
                    <ShareWithModal />
                    <SharingDetails />
                  </>
                )}
                <Modal
                  visible={enableConfirmationVisible}
                  onCancel={() => setEnableConfirmationVisible(false)}
                  title={tn('enable_insights_title')}
                  okText={tc('enable')}
                  centered
                  confirmLoading={enableInsightsLoading}
                  onOk={() => {
                    enableInsights(InstanceFeatures.INSIGHTS_PROVIDER)
                      .unwrap()
                      .then(() => setEnableConfirmationVisible(false))
                      .catch((error) => message.error(getRtkQueryErrorMessage(error)));
                  }}>
                  <Text beDangerous>{tn('enable_insights_confirmation')}</Text>
                </Modal>
              </UnifiedDataCardAuthoringContextProvider>
            </DatasetAuthoringContextProvider>
          </DataCardAuthoringContextProvider>
        </InsightsSidebarContextProvider>
      </DataCardSettingsContextProvider>
    )
  );
};

export default InsightsStudio;
