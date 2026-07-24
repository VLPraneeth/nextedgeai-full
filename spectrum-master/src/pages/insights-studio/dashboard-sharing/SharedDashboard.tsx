import { RouteComponentProps, navigate } from '@reach/router';
import { FetchBaseQueryError } from '@reduxjs/toolkit/dist/query';
import { Select } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { Responsive, WidthProvider } from 'react-grid-layout';
import { useSelector } from 'react-redux';

import { HStack } from 'components/layout';
import { SyncariLogo } from 'components/navigation/Navigation';
import Redirect from 'components/Redirect';
import { ScrollableArea } from 'components/scrollable-area/ScrollableArea';
import TabPanelSpin from 'components/TabPanelSpin';
import { Toolbar } from 'components/toolbar';
import { Text } from 'components/typography';
import { useUtcTimeInUsersTimezone } from 'hooks/moment';
import { useEnhancedDispatch } from 'hooks/redux';
import MainContent from 'pages/MainContent';
import { useGetAllSharedDashboardsQuery, useGetSharedDashboardQuery } from 'store/insights-studio';
import { AllSharedDashboard } from 'store/insights-studio/types';
import { selectCurrentInstanceId, selectUserEmail } from 'store/user/selectors';
import { getProfile, logout } from 'store/user/thunks';
import { HTTP } from 'utils/AjaxUtil';
import { navigateTo } from 'utils/AppUtil';
import { tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { dashboardGridLayoutSettings } from '../components/dashboard-layout/DashboardLayout';
import { DataCard } from '../components/data-card/DataCard';
import { DataCardSettingsContextProvider, Settings } from '../settings';
import { DashboardExpiredModal } from './DashboardExpired';
import { ExitToAppButton } from './ExitToAppButton';

import './SharedDashboard.scss';

const tn = tNamespaced('InsightsStudio');

export default function SharedDashboard({
  dashboardId,
  email: emailFromUrl,
}: RouteComponentProps & { dashboardId: string; email: string }) {
  const {
    data: availableDashboards,
    refetch,
    isLoading: availableDashboardsLoading,
  } = useGetAllSharedDashboardsQuery();

  const [selectedDashboard, setSelectedDashboad] = useState<AllSharedDashboard>();
  const [expiredModalVisible, setExpiredModalVisible] = useState(true);
  const emailDecoded = atob(emailFromUrl);
  const dispatch = useEnhancedDispatch();
  const loggedinUserEmail = useSelector(selectUserEmail);
  const currentInstanceNextEdgeId = useSelector(selectCurrentInstanceId);
  const navigateHome = () => navigateTo(RouteConstants.HOME);
  const utcToLocal = useUtcTimeInUsersTimezone();
  const ResponsiveGridLayout = useMemo(() => WidthProvider(Responsive), []);
  const {
    data: dashboard,
    error,
    isLoading: dashboardLoading,
    isFetching: dashboardFetching,
  } = useGetSharedDashboardQuery(dashboardId);
  const sharedDashboardError = error as FetchBaseQueryError | undefined;

  useEffect(() => {
    if (loggedinUserEmail !== undefined && emailDecoded !== loggedinUserEmail) {
      dispatch(logout(true));
      // Use assign to clear the old cookie from the API requests
      window.location.assign(
        makeUrl(RouteConstants.INSIGHTS_STUDIO_SHARED_DASHBOARD_ENTER_PASSWORD, {
          dashboardId,
          email: emailFromUrl,
        })
      );
    }
  }, [dispatch, emailFromUrl, emailDecoded, loggedinUserEmail, dashboardId]);

  useEffect(() => {
    setSelectedDashboad(availableDashboards?.find((dash) => dash.dashboardId === dashboardId));
  }, [dashboardId, availableDashboards]);

  useEffect(() => {
    if (dashboard || !loggedinUserEmail) {
      dispatch(getProfile());
    }
  }, [dispatch, dashboard, loggedinUserEmail]);

  if (availableDashboardsLoading) {
    return <TabPanelSpin spinning />;
  }

  if (
    sharedDashboardError?.status === HTTP.UNAUTHORIZED ||
    (loggedinUserEmail !== undefined && emailDecoded !== loggedinUserEmail)
  ) {
    window.location.assign(
      makeUrl(RouteConstants.INSIGHTS_STUDIO_SHARED_DASHBOARD_ENTER_PASSWORD, {
        dashboardId,
        email: emailFromUrl,
      })
    );
    return null;
  }

  if (
    loggedinUserEmail !== undefined &&
    emailDecoded === loggedinUserEmail &&
    (sharedDashboardError?.status === HTTP.FORBIDDEN || sharedDashboardError?.status === HTTP.NOT_FOUND)
  ) {
    if (!availableDashboards?.length) {
      return (
        <Redirect
          redirectTo={makeUrl(RouteConstants.INSIGHTS_STUDIO_SHARED_DASHBOARD_EXPIRED, {
            dashboardId,
            email: emailFromUrl,
          })}
        />
      );
    } else {
      const nextDashboardId =
        availableDashboards?.find((dashboard) => dashboard.dashboardInstanceId === currentInstanceNextEdgeId)
          ?.dashboardId || availableDashboards[0]?.dashboardId;
      return (
        <DashboardExpiredModal
          handleClose={() => {
            setExpiredModalVisible(false);
            refetch();

            navigate(
              makeUrl(RouteConstants.INSIGHTS_STUDIO_SHARED_DASHBOARD, {
                dashboardId: nextDashboardId,
                email: emailFromUrl,
              })
            );
          }}
          visible={expiredModalVisible}
        />
      );
    }
  }

  const layouts =
    dashboard?.dataCards?.map((card) => {
      return {
        ...card.layout,
        i: card.id,
        isDraggable: false,
        isResizable: false,
        minH: 1,
        minW: 3,
        maxH: 4,
      };
    }) ?? [];

  if ((dashboard?.dataCards?.length ?? 0) > layouts.length) {
    // prevents crash from rendering before `layouts` has been created
    return null;
  }

  return (
    <DataCardSettingsContextProvider>
      <div className="shared-dashboard">
        <div className="shared-dashboard__header">
          <SyncariLogo onClick={navigateHome} isCollapsed={false} />
        </div>

        <MainContent>
          <div>
            <Toolbar
              className="shared-dashboard__toolbar"
              leftChildren={
                <HStack>
                  {availableDashboards?.length &&
                    (availableDashboards.length === 1 ? (
                      <div className="shared-dashboard__dashboardName">
                        {availableDashboards[0].dashboardDiplayName}
                      </div>
                    ) : (
                      <Select
                        className="shared-dashboard__dashboardSelect"
                        onChange={(value: string) => {
                          navigate(
                            makeUrl(RouteConstants.INSIGHTS_STUDIO_SHARED_DASHBOARD, {
                              dashboardId: value,
                              email: emailFromUrl,
                            })
                          );
                        }}
                        defaultValue={dashboardId}>
                        {availableDashboards.map((dashboard) => (
                          <Select.Option value={dashboard.dashboardId}>{dashboard.dashboardDiplayName}</Select.Option>
                        ))}
                      </Select>
                    ))}
                  {selectedDashboard?.expiredTime && (
                    <Text className="shared-dashboard__expiry" beDangerous>
                      {tn('InsightsSharing.dashboard_expires_on', {
                        datetime: utcToLocal(selectedDashboard?.expiredTime),
                      })}
                    </Text>
                  )}
                </HStack>
              }
              children={<ExitToAppButton className="shared-dashboard__exit-button" />}
            />

            {dashboardFetching || dashboardLoading ? (
              <TabPanelSpin spinning />
            ) : (
              <ScrollableArea>
                <div className="dashboard-layout">
                  <ResponsiveGridLayout
                    style={{ minHeight: '100%' }}
                    key={dashboardId}
                    breakpoints={{ sm: 480 }}
                    cols={{ sm: 12 }}
                    layouts={{ sm: layouts }}
                    rowHeight={dashboardGridLayoutSettings.rowHeight}
                    margin={[dashboardGridLayoutSettings.margin, dashboardGridLayoutSettings.margin]}
                    isDroppable={false}>
                    {dashboard?.dataCards?.map((card, i) => {
                      return (
                        <div key={card.id}>
                          <DataCard
                            dashboardId={dashboard.id}
                            description={card.description ?? ''}
                            id={card.id}
                            name={card.displayName ?? ''}
                            layout={layouts[i]}
                            isDraft={false}
                          />
                        </div>
                      );
                    })}
                  </ResponsiveGridLayout>
                </div>
              </ScrollableArea>
            )}
          </div>
        </MainContent>
        <Settings />
      </div>
    </DataCardSettingsContextProvider>
  );
}
