import { navigate, useLocation, useMatch } from '@reach/router';
import { Button, Icon, message, Tooltip } from 'antd';
import { useEffect, useMemo, useState } from 'react';

import { ReactComponent as DataCardIcon } from 'assets/icons/dashboard.svg';
import { ReactComponent as InfoIcon } from 'assets/icons/info-icon-solid.svg';
import { ReactComponent as RowTable } from 'assets/icons/row-table.svg';
import { InlineTabs, InlineTab } from 'components/InlineTabs';
import { HStack } from 'components/layout';
import { useInsightsViewContext } from 'pages/insights-studio/context/InsightsViewContext';
import { useDashboards } from 'pages/insights-studio/utils';
import { useGetDataStoreQuery, useLazyGetDataStoreLagQuery } from 'store/datastore/api';
import { useGetTSLiveboardsQuery, useShareTSObjectMutation } from 'store/insights-studio';
import { InstanceFeatures, useGetFeatureStatusQuery } from 'store/instance-feature/api';
import { tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import './InsightsThoughtSpotTabs.scss';
import { useUserRolesForCurrentInstance } from 'store/user/hooks';
import Modal from 'components/Modal';
import { Text } from 'components/typography';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';

const tn = tNamespaced('InsightsStudio.thoughtspot');

export type TSTabs = 'dashboards' | 'datasets' | 'search' | 'datacards';

export const InsightsThoughtSpotTabs = ({
  setCurrentTab,
  currentTab,
}: {
  setCurrentTab: (tab: string) => void;
  currentTab?: string;
}) => {
  const { refetch } = useGetTSLiveboardsQuery();
  const { setInsightsView, isThoughtSpotEnabled } = useInsightsViewContext();
  const availableDashboards = useDashboards();
  const hasUserCreatedDashboards = useMemo(() => {
    return !!availableDashboards.filter((ds) => !ds.seeded && !ds.isExample).length;
  }, [availableDashboards]);
  const { data: insightsFeature } = useGetFeatureStatusQuery(InstanceFeatures.INSIGHTS);
  const { data: dataStore } = useGetDataStoreQuery();
  const [getDataStoreLags, { data: lags, error: lagsError }] = useLazyGetDataStoreLagQuery();
  const location = useLocation();

  const { roles } = useUserRolesForCurrentInstance();
  const instanceAndHigher = roles.superAdmin || roles.instanceAdmin || roles.admin;

  const [shareDatacard, { isLoading: isSharing }] = useShareTSObjectMutation();
  const [shareConfirmationVisible, setShareConfirmationVisible] = useState(false);

  // const [currentTSEntityId, setCurrentTSEntityId] = useState('');
  let currentTSEntityId = '';

  if (currentTab === 'dashboards') {
    const dashboardIdTSMatch = useMatch('/insights-studio/ts/dashboards/:dashboardId/*');
    if (dashboardIdTSMatch?.dashboardId) {
      currentTSEntityId = dashboardIdTSMatch.dashboardId;
    } else {
      currentTSEntityId = '';
    }
  }

  if (currentTab === 'datacards') {
    const datacardIdTSMatch = useMatch('/insights-studio/ts/datacards/:datacardId/*');
    if (datacardIdTSMatch?.datacardId) {
      currentTSEntityId = datacardIdTSMatch?.datacardId;
    } else {
      currentTSEntityId = '';
    }
  }

  useEffect(() => {
    if (dataStore) {
      getDataStoreLags();
    }
  }, [getDataStoreLags, dataStore]);

  const tooltipText = useMemo(() => {
    const hasLag = !!lags?.filter((lag) => lag.pendingRecords).length;
    if (insightsFeature?.status === 'activating') {
      return tn('enable_insights_activating');
    } else if (insightsFeature?.status === 'active' && !lagsError && hasLag) {
      return tn('enable_insights_loading_description');
    }
    return '';
  }, [lags, lagsError, insightsFeature?.status]);

  return (
    <>
      <InlineTabs
        selectedTab={currentTab || ''}
        onChange={(key) => {
          if (key === 'dashboards') {
            if (location.pathname.startsWith(RouteConstants.INSIGHTS_STUDIO_TS_DASHBOARDS)) {
              setCurrentTab(key);
            } else {
              navigate(RouteConstants.INSIGHTS_STUDIO);
            }
          } else {
            setCurrentTab(key);
          }
          refetch();
        }}
        className="thought-spot-tabs">
        <HStack justify="space-between" grow>
          <HStack spacing="z">
            <InlineTab className="thought-spot-tabs__tab" id="dashboards">
              <Icon type="appstore" />
              {tn('dashboards')}
              {tooltipText && (
                <Tooltip title={tooltipText} mouseEnterDelay={0.3} placement="bottom">
                  <InfoIcon className="thought-spot-tabs__tab__info-icon" />
                </Tooltip>
              )}
            </InlineTab>
            <InlineTab className="thought-spot-tabs__tab " id="datasets">
              <RowTable className="thought-spot-tabs__tab__dataset-icon" />
              {tn('datasets')}
            </InlineTab>
            <InlineTab className="thought-spot-tabs__tab" id="datacards">
              <DataCardIcon className="thought-spot-tabs__tab__dataset-icon" />
              {tn('datacards')}
            </InlineTab>
            <InlineTab className="thought-spot-tabs__tab" id="search">
              <Icon type="search" />
              {tn('search')}
            </InlineTab>
          </HStack>

          <HStack spacing="md" justify="space-between" className="top_bar_actions">
            {isThoughtSpotEnabled && (hasUserCreatedDashboards || instanceAndHigher) && (
              <>
                {currentTSEntityId && (
                  <>
                    <Button
                      loading={isSharing}
                      onClick={() => setShareConfirmationVisible(true)}
                      icon="share-alt"
                      type="primary"
                      className="top_bar_actions-share">
                      {currentTab === 'dashboards' ? tn('share_dashboard') : tn('share_datacard')}
                    </Button>
                    <Modal
                      visible={shareConfirmationVisible}
                      onCancel={() => setShareConfirmationVisible(false)}
                      title={tn('share_insights_title')}
                      okText={tn('share_ok')}
                      centered
                      confirmLoading={isSharing}
                      onOk={() => {
                        shareDatacard({
                          metadataType: currentTab === 'dashboards' ? 'LIVEBOARD' : 'ANSWER',
                          metadataId: currentTSEntityId,
                        })
                          .then(() => {
                            setShareConfirmationVisible(false);
                            const successMsg: string = `${
                              currentTab === 'dashboards' ? 'Dashboard' : 'Datacard'
                            } shared successfully.`;
                            message.success(successMsg);
                          })
                          .catch((error) => message.error(getRtkQueryErrorMessage(error)));
                      }}>
                      <Text beDangerous>{tn('share_insights_confimation', { type: currentTab })}</Text>
                    </Modal>
                  </>
                )}
                {insightsFeature?.status === 'active' && (
                  <Button
                    onClick={() => {
                      setInsightsView('legacy');
                      navigate(RouteConstants.INSIGHTS_STUDIO);
                    }}>
                    {tn('switch_to_legacy_insights')}
                  </Button>
                )}
              </>
            )}
          </HStack>
        </HStack>
      </InlineTabs>
    </>
  );
};
