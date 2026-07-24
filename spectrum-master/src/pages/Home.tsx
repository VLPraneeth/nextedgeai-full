//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Spin } from 'antd';
import { useEffect } from 'react';

import Redirect from 'components/Redirect';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import useConnectMessageStream from 'hooks/useConnectMessageStream';
import useDisplayApplicationError from 'hooks/useDisplayApplicationError';
import MainPageLayout from 'pages/MainPageLayout';
import { useGetAllSharedDashboardsQuery } from 'store/insights-studio';
import { useUserRolesForCurrentInstance } from 'store/user/hooks';
import {
  selectCurrentInstanceId,
  selectUserEmail,
  selectUserId,
  selectUserPasswordExpired,
  selectUserRoles,
} from 'store/user/selectors';
import { getProfile, getUserPreference } from 'store/user/thunks';
import CapConstants from 'utils/CapConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

// const tn = tNamespaced('InsightsStudio.thoughtspot');

// TODO: Remove the props drilling pattern here sent from HomeContainer
const Home = (props: any) => {
  useDisplayApplicationError();
  useConnectMessageStream();

  const dispatch = useEnhancedDispatch();

  useEffect(() => {
    dispatch(getProfile());
    dispatch(getUserPreference());
  }, [dispatch]);

  const userId = useEnhancedSelector(selectUserId);
  const passwordExpired = useEnhancedSelector(selectUserPasswordExpired);
  const email = useEnhancedSelector(selectUserEmail);
  const roles = useEnhancedSelector(selectUserRoles);
  const currentInstanceNextEdgeId = useEnhancedSelector(selectCurrentInstanceId);
  const { roles: adminRoles } = useUserRolesForCurrentInstance();
  const currentInstanceRoles = roles?.[currentInstanceNextEdgeId] || [];
  const hasOnlyDashboardLightViewerRole =
    currentInstanceRoles.length === 1 && currentInstanceRoles.includes(CapConstants.DASHBOARD_LIGHT_VIEWER);
  const { data: availableDashboards, isLoading: availableDashboardsLoading } = useGetAllSharedDashboardsQuery(
    undefined,
    {
      skip: !hasOnlyDashboardLightViewerRole,
    }
  );

  if (userId && passwordExpired) {
    return <Redirect redirectTo={RouteConstants.PASSWORD_RESET} />;
  }

  if (
    userId &&
    !availableDashboardsLoading &&
    availableDashboards &&
    hasOnlyDashboardLightViewerRole &&
    !(adminRoles.admin || adminRoles.instanceAdmin || adminRoles.orgAdmin || adminRoles.superAdmin)
  ) {
    // User doesn't have any shared dashboard.
    if (!availableDashboards?.length) {
      return (
        <Redirect
          redirectTo={makeUrl(RouteConstants.INSIGHTS_STUDIO_SHARED_DASHBOARD_EXPIRED, {
            email: btoa(email),
          })}
        />
      );
    }
    const dashboardId =
      availableDashboards?.find((dashboard) => dashboard.dashboardInstanceId === currentInstanceNextEdgeId)
        ?.dashboardId || availableDashboards[0]?.dashboardId;

    return (
      <Redirect
        redirectTo={makeUrl(RouteConstants.INSIGHTS_STUDIO_SHARED_DASHBOARD, {
          dashboardId,
          email: btoa(email),
        })}
      />
    );
  }

  return (
    <>
      {userId && !availableDashboardsLoading ? (
        <MainPageLayout {...props} />
      ) : (
        <Spin>
          <div data-testid="loading-page" className="loading-page" />
        </Spin>
      )}
    </>
  );
};

export default Home;
