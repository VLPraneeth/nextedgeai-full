import produce, { Draft } from 'immer';

import AppConstants from 'utils/AppConstants';

import { GET_SYNC_ERRORS_PENDING, GET_SYNC_ERRORS_FULFILLED, GET_SYNC_ERRORS_FAILED, LogsState } from './types';

export const initialState: LogsState = {
  syncErrors: {
    listData: null,
    fetchStatus: AppConstants.FETCH_STATUS.IDLE,
  },
};

export default produce((draft: Draft<LogsState>, action) => {
  switch (action.type) {
    case GET_SYNC_ERRORS_PENDING:
      draft.syncErrors.fetchStatus = AppConstants.FETCH_STATUS.LOADING;
      break;
    case GET_SYNC_ERRORS_FULFILLED:
      draft.syncErrors.fetchStatus = AppConstants.FETCH_STATUS.SUCCESS;
      draft.syncErrors.listData = action.payload.data;
      break;
    case GET_SYNC_ERRORS_FAILED:
      draft.syncErrors.fetchStatus = AppConstants.FETCH_STATUS.ERROR;
      break;
  }
}, initialState);
