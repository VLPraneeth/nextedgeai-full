import { Moment } from 'moment-timezone';

import { PromiseThunkAction } from 'store/types';
import { get, post } from 'utils/AjaxUtil';
import { getErrorMessage } from 'utils/AppUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { formatDatesInParams } from 'utils/DateUtil';
import { makeUrl } from 'utils/UrlUtil';

import { getSyncErrorsPending, getSyncErrorsFulfilled, getSyncErrorsFailed } from './actions';
import { SystemFilterQuery } from './types';

export interface SyncErrorsParams {
  startDate: Moment;
  endDate: Moment;
  connectorName?: string;
  operation?: string;
  entityId?: string;
  syncariRecordId?: string;
  syncariEntityName?: string;
}

export interface GetSyncErrorsParams extends SyncErrorsParams {
  pageNumber: number;
  count: number;
}

const ALL_VALUE = 'all';

export const getSyncErrors = ({
  count,
  pageNumber,
  entityId,
  operation,
  syncariRecordId,
  syncariEntityName,
  connectorName,
  ...params
}: GetSyncErrorsParams): PromiseThunkAction => (dispatch) => {
  dispatch(getSyncErrorsPending());

  const url = makeUrl(DataUrlConstants.GET_SYNC_ERRORS, formatDatesInParams(params), {
    count,
    pageNumber,
    syncariRecordId,
    syncariEntityName: syncariEntityName === ALL_VALUE ? undefined : syncariEntityName,
    connectorName: connectorName === ALL_VALUE ? undefined : connectorName,
    entityId: entityId === ALL_VALUE ? undefined : entityId,
    operation: operation === ALL_VALUE ? undefined : operation,
  });

  return get(url)
    .then((resp) => {
      dispatch(getSyncErrorsFulfilled(resp.data));
    })
    .catch((err) => {
      dispatch(getSyncErrorsFailed(getErrorMessage(err)));
    });
};

export interface GetSyncErrorsByMessageParams extends SystemFilterQuery {
  pageNumber: GetSyncErrorsParams['pageNumber'];
  count: GetSyncErrorsParams['count'];
}

export const getSyncErrorsByMessage = ({
  count,
  pageNumber,
  message,
  syncCycleId,
  nodeId,
  ...params
}: GetSyncErrorsByMessageParams): PromiseThunkAction => (dispatch) => {
  dispatch(getSyncErrorsPending());
  return post(makeUrl(DataUrlConstants.GET_SYNC_ERRORS_BY_MESSAGE, { syncCycleId, nodeId }, { count, pageNumber }), {
    message,
  })
    .then((resp) => dispatch(getSyncErrorsFulfilled(resp.data)))
    .catch((err) => dispatch(getSyncErrorsFailed(getErrorMessage(err))));
};
