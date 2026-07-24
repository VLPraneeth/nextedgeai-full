import { useCallback, useEffect, useMemo } from 'react';

import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import AppConstants from 'utils/AppConstants';

import { ALL_DASHBOARDS_CATEGORY } from './constants';
import { thunks, GetWidgetArgs } from './slice';
import { makeWidgetKey } from './utils';

export const useDashboardsList = (category?: string) => {
  // if we're not given a category, then we'll look up the 'all' key in our reducer, but leave the category off
  // in our fetch
  const categoryKey = category ?? ALL_DASHBOARDS_CATEGORY;

  const dispatch = useEnhancedDispatch();
  const dashboards = useEnhancedSelector((state) =>
    state.newDashboard.dashboardsByCategory[categoryKey]?.map(
      (dashboardName) => state.newDashboard.dashboards[dashboardName]
    )
  );
  const error = useEnhancedSelector((state) => state.newDashboard.dashboardsByCategoryError[categoryKey]);
  const status = useEnhancedSelector(
    (state) => state.newDashboard.dashboardsByCategoryStatus[categoryKey] ?? AppConstants.FETCH_STATUS.IDLE
  );

  useEffect(() => {
    if (status === AppConstants.FETCH_STATUS.IDLE) {
      dispatch(thunks.getDashboards(category));
    }
  }, [category, dispatch, status]);

  return useMemo(
    () => ({
      dashboards: dashboards ?? [],
      error,
      loading: status === AppConstants.FETCH_STATUS.LOADING,
      status,
    }),
    [dashboards, error, status]
  );
};

export const useDashboard = (dashboardName: string) => {
  const dispatch = useEnhancedDispatch();
  const dashboard = useEnhancedSelector((state) => state.newDashboard.dashboards[dashboardName]);
  const error = useEnhancedSelector((state) => state.newDashboard.dashboardError[dashboardName]);
  const status = useEnhancedSelector(
    (state) => state.newDashboard.dashboardStatus[dashboardName] ?? AppConstants.FETCH_STATUS.IDLE
  );

  useEffect(() => {
    if (status === AppConstants.FETCH_STATUS.IDLE) {
      dispatch(thunks.getDashboard(dashboardName));
    }
  }, [dashboardName, dispatch, status]);

  return useMemo(
    () => ({
      dashboard,
      error,
      loading: status === AppConstants.FETCH_STATUS.LOADING,
      status,
    }),
    [dashboard, error, status]
  );
};

export type UseWidgetParams = GetWidgetArgs;

export const useWidget = ({ dashboardName, widgetName }: UseWidgetParams) => {
  const widgetKey = makeWidgetKey(dashboardName, widgetName);

  const dispatch = useEnhancedDispatch();
  const error = useEnhancedSelector((state) => state.newDashboard.dashboardError[widgetKey]);
  const status = useEnhancedSelector(
    (state) => state.newDashboard.widgetStatus[widgetKey] ?? AppConstants.FETCH_STATUS.IDLE
  );
  const widget = useEnhancedSelector((state) => state.newDashboard.widgets[widgetKey]);

  const fetchWidget = useCallback(() => {
    dispatch(thunks.getWidget({ dashboardName, widgetName }));
  }, [dispatch, dashboardName, widgetName]);

  useEffect(() => {
    if (status === AppConstants.FETCH_STATUS.IDLE) {
      fetchWidget();
    }
  }, [fetchWidget, status]);

  return useMemo(
    () => ({
      widget,
      error,
      loading: status === AppConstants.FETCH_STATUS.LOADING,
      status,
      refetch: fetchWidget,
    }),
    [widget, error, status, fetchWidget]
  );
};
