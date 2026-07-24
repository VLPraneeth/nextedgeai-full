//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { injectEndpoints } from '../api';
import { ExecuteQuickStart, QuickStartHistory, QuickStartHistoryParams, QuickStarts } from './types';

const quickStartApi = injectEndpoints({
  endpoints: (builder) => ({
    executeQuickStart: builder.mutation<ExecuteQuickStart, ExecuteQuickStart>({
      query: (params) => {
        const { quickStartName, ...body } = params;
        return {
          url: makeUrl(DataUrlConstants.QUICK_START_LEGACY_EXECUTE, { quickStartName }),
          method: 'POST',
          body,
        };
      },
    }),
    getDynamicSteps: builder.mutation<ExecuteQuickStart, ExecuteQuickStart>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.QUICK_START_LEGACY_DYNAMIC_STEPS, params),
          method: 'POST',
          body: params,
        };
      },
    }),
    getQuickStartsLegacy: builder.query<QuickStarts, void>({
      query: () => ({ url: DataUrlConstants.QUICK_STARTS_LEGACY }),
    }),
    getQuickStartHistory: builder.query<QuickStartHistory, QuickStartHistoryParams>({
      query: (params) => ({ url: makeUrl(DataUrlConstants.QUICK_START_LEGACY_HISTORY, params) }),
    }),
  }),
  overrideExisting: false,
});

export const {
  useExecuteQuickStartMutation,
  useGetDynamicStepsMutation,
  useGetQuickStartHistoryQuery,
  useGetQuickStartsLegacyQuery,
} = quickStartApi;
