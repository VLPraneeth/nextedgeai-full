import { ResponseError } from 'store/types';

import { GET_SYNC_ERRORS_PENDING, GET_SYNC_ERRORS_FULFILLED, GET_SYNC_ERRORS_FAILED } from './types';

export const getSyncErrorsPending = () => ({
  type: GET_SYNC_ERRORS_PENDING,
});

export const getSyncErrorsFulfilled = (data: any) => ({
  type: GET_SYNC_ERRORS_FULFILLED,
  payload: { data },
});

export const getSyncErrorsFailed = (error: ResponseError | {}) => ({
  type: GET_SYNC_ERRORS_FAILED,
  payload: { error },
});
