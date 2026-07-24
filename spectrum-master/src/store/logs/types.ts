import { PageInfo } from 'store/data-studio';
import { FetchStatus, ResponseError } from 'store/types';

export const GET_SYNC_ERRORS_PENDING = 'reports/syncErrors/pending';
export const GET_SYNC_ERRORS_FULFILLED = 'reports/syncErrors/fulfilled';
export const GET_SYNC_ERRORS_FAILED = 'reports/syncErrors/failed';

interface GetSyncErrorsPending {
  type: typeof GET_SYNC_ERRORS_PENDING;
}

interface GetSyncErrorsFulfilled {
  type: typeof GET_SYNC_ERRORS_FULFILLED;
  payload: {
    data: SyncErrorsListData;
  };
}

interface GetSyncErrorsFailed {
  type: typeof GET_SYNC_ERRORS_FAILED;
  payload: {
    error: ResponseError;
  };
}

export type LogsAction = GetSyncErrorsPending | GetSyncErrorsFulfilled | GetSyncErrorsFailed;

export interface SyncErrorRecord {
  batchId: string;
  connectorId: string;
  connectorName: string;
  errorCode: string;
  errorDetails: string;
  externalEntityName: string;
  externalRecordId: string;
  occuredTime: string;
  operation: string;
  syncariEntityName: string;
  syncariRecordId: string;
}

interface SyncErrorsListData {
  pageInfo: PageInfo;
  records: SyncErrorRecord[];
}

export interface LogsState {
  syncErrors: {
    listData: SyncErrorsListData | null;
    fetchStatus: FetchStatus;
  };
}

export interface SystemFilterQuery {
  nodeId?: string;
  message?: string;
  syncCycleId?: string;
}
