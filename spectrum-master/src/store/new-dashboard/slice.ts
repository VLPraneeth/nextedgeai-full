import { createAsyncThunk, createSlice, SerializedError } from '@reduxjs/toolkit';

import { Dashboard, Widget } from 'pages/data-quality-studio/types';
import { EMPTY_OBJECT } from 'store/constants';
import { FetchStatus } from 'store/types';
import { get } from 'utils/AjaxUtil';
import AppConstants from 'utils/AppConstants';
import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { ALL_DASHBOARDS_CATEGORY } from './constants';
import { makeWidgetKey } from './utils';

const getDashboards = createAsyncThunk<Dashboard[], string | undefined>('dashboard/dashboards', (category?: string) => {
  return get<Dashboard[]>(makeUrl(DataUrlConstants.GET_DASHBOARDS, undefined, { category })).then((resp) => resp.data);
});

const getDashboard = createAsyncThunk<Dashboard, string>('dashboard/dashboard', (dashboardName: string) => {
  return get<Dashboard>(makeUrl(DataUrlConstants.GET_DASHBOARD, { dashboardName })).then((resp) => resp.data);
});

export interface GetWidgetArgs {
  dashboardName: string;
  widgetName: string;
}

const getWidget = createAsyncThunk<Widget, GetWidgetArgs>(
  'dashboard/widget',
  ({ dashboardName, widgetName }: GetWidgetArgs) => {
    return get<Widget>(makeUrl(DataUrlConstants.GET_WIDGET, { dashboardName, widgetName })).then((resp) => resp.data);
  }
);

export interface NewDashboardState {
  dashboardsByCategory: Record<string, string[]>;
  dashboardsByCategoryError: Record<string, SerializedError>;
  dashboardsByCategoryStatus: Record<string, FetchStatus>;
  dashboards: Record<string, Dashboard>;
  dashboardError: Record<string, SerializedError>;
  dashboardStatus: Record<string, FetchStatus>;
  widgets: Record<string, Widget>;
  widgetError: Record<string, SerializedError>;
  widgetStatus: Record<string, FetchStatus>;
}

export const initialState: NewDashboardState = {
  dashboardsByCategory: {
    // set up a default empty list for the 'all' category
    [ALL_DASHBOARDS_CATEGORY]: [],
  },
  dashboardsByCategoryError: {},
  dashboardsByCategoryStatus: {},
  dashboards: {},
  dashboardError: {},
  dashboardStatus: {},
  widgets: {},
  widgetError: {},
  widgetStatus: {},
};

const newDashboardSlice = createSlice({
  name: 'newDashboard',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    // get Dashboards
    builder.addCase(getDashboards.pending, (state, action) => {
      const { arg: category = ALL_DASHBOARDS_CATEGORY } = action.meta;
      state.dashboardsByCategoryStatus[category] = AppConstants.FETCH_STATUS.LOADING;
      delete state.dashboardsByCategoryError[category];
    });
    builder.addCase(getDashboards.fulfilled, (state, action) => {
      const { arg: category = ALL_DASHBOARDS_CATEGORY } = action.meta;

      const newDashboardNames = action.payload.map((dashboard) => dashboard.name);

      state.dashboardsByCategoryStatus[category] = AppConstants.FETCH_STATUS.SUCCESS;
      action.payload.forEach(({ widgets, ...dashboard }) => {
        // TODO: this might change if the list api starts returning a sub selection of the Dashboard data
        const priorDashboard = state.dashboards[dashboard.name] || EMPTY_OBJECT;

        state.dashboards[dashboard.name] = {
          ...priorDashboard,
          ...dashboard,
        };
      });

      state.dashboardsByCategory[category] = newDashboardNames;
      if (category !== ALL_DASHBOARDS_CATEGORY) {
        // append the new dashboard name to the 'all' list if we were requesting a specific category
        state.dashboardsByCategory[ALL_DASHBOARDS_CATEGORY] = Array.from(
          new Set(...state.dashboardsByCategory[ALL_DASHBOARDS_CATEGORY], ...newDashboardNames)
        );
      }
    });
    builder.addCase(getDashboards.rejected, (state, action) => {
      const { arg: category = ALL_DASHBOARDS_CATEGORY } = action.meta;
      state.dashboardsByCategoryStatus[category] = AppConstants.FETCH_STATUS.ERROR;
      state.dashboardsByCategoryError[category] = action.error;
    });

    // get Dashboard
    builder.addCase(getDashboard.pending, (state, action) => {
      const { arg: dashboardName } = action.meta;
      state.dashboardStatus[dashboardName] = AppConstants.FETCH_STATUS.LOADING;
      delete state.dashboardError[dashboardName];
    });
    builder.addCase(getDashboard.fulfilled, (state, action) => {
      const { arg: dashboardName } = action.meta;
      state.dashboardStatus[dashboardName] = AppConstants.FETCH_STATUS.SUCCESS;
      state.dashboards[dashboardName] = action.payload;
    });
    builder.addCase(getDashboard.rejected, (state, action) => {
      const { arg: dashboardName } = action.meta;
      state.dashboardStatus[dashboardName] = AppConstants.FETCH_STATUS.ERROR;
      state.dashboardError[dashboardName] = action.error;
    });

    // get Widget
    builder.addCase(getWidget.pending, (state, action) => {
      const { dashboardName, widgetName } = action.meta.arg;
      const key = makeWidgetKey(dashboardName, widgetName);
      state.widgetStatus[key] = AppConstants.FETCH_STATUS.LOADING;
      delete state.widgetError[key];
    });
    builder.addCase(getWidget.fulfilled, (state, action) => {
      const { dashboardName, widgetName } = action.meta.arg;
      const key = makeWidgetKey(dashboardName, widgetName);
      state.widgetStatus[key] = AppConstants.FETCH_STATUS.SUCCESS;
      state.widgets[key] = action.payload;
    });
    builder.addCase(getWidget.rejected, (state, action) => {
      const { dashboardName, widgetName } = action.meta.arg;
      const key = makeWidgetKey(dashboardName, widgetName);
      state.widgetStatus[key] = AppConstants.FETCH_STATUS.ERROR;
      state.widgetError[key] = action.error;
    });
  },
});

export const { reducer } = newDashboardSlice;

export const thunks = {
  getDashboards,
  getDashboard,
  getWidget,
};
