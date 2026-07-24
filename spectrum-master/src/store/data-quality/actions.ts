import { createAsyncThunk } from '@reduxjs/toolkit';
import { find, forEach } from 'lodash';
import { clamp } from 'utils';

import { selectNewDashboardDashboards } from 'store/new-dashboard';
import { thunks } from 'store/new-dashboard/slice';
import { RootState } from 'store/types';

import { dfiRecalculationUpdate } from './slice';

export const dfiRecalculationUpdateThunk = createAsyncThunk<
  void,
  {
    payload: {
      completed: string;
      entityId: string;
      progressPercentage: string;
    };
  },
  { state: RootState }
>('DFI_RECALCULATION_UPDATE', (action, { dispatch, getState }) => {
  // The backend has some issue where they're only able to send string on
  // this map. Parsing here to get boolean/number
  const progressPercentage = clamp(JSON.parse(action.payload.progressPercentage), 0, 100);
  const completed = JSON.parse(action.payload.completed);
  const { entityId } = action.payload;

  dispatch(
    dfiRecalculationUpdate({
      // Sometimes there's an update message sent after the completion message
      // with completed: false and progressPercentage: 100.
      completed: completed || progressPercentage === 100,
      progressPercentage,
      entityId,
    })
  );

  // Refresh all the dashboard widgets when the DFI recalculation is complete
  if (completed) {
    const dashboards = selectNewDashboardDashboards(getState());

    const entityDashboard = find(dashboards, { entityId });
    if (entityDashboard) {
      forEach(entityDashboard.widgets, (widget) => {
        dispatch(thunks.getWidget({ dashboardName: entityDashboard.name, widgetName: widget.name }));
      });
    }

    const overviewDashboard = find(dashboards, { entityId: 'dqsOverview' });
    if (overviewDashboard) {
      forEach(overviewDashboard.widgets, (widget) => {
        dispatch(thunks.getWidget({ dashboardName: overviewDashboard.name, widgetName: widget.name }));
      });
    }
  }
});
