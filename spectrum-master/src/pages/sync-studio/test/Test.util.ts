//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { ValuesOf } from 'utils/TypeUtils';

export const OVERVIEW_ID = 'overview';

export const TEST_STATUS = {
  COMPLETED: 'completed',
  QUEUED: 'queued',
  PENDING: 'pending',
  RUNNING: 'running',
  FAILED: 'failed',
  SUCCESS: 'success',
  ERROR: 'error',
  ABORTED: 'aborted',
  NEW: 'NEW',
  PROCESSING: 'PROCESSING',
};

export type TestStatus = ValuesOf<typeof TEST_STATUS>;

const STATUSES_WITH_RESULTS = [TEST_STATUS.COMPLETED, TEST_STATUS.FAILED, TEST_STATUS.SUCCESS, TEST_STATUS.ERROR];

export const doesTestStatusHaveResult = (status?: TestStatus): boolean =>
  !!status && STATUSES_WITH_RESULTS.includes(status);
