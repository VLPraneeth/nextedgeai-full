//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import DataUrlConstants from 'utils/DataUrlConstants';

import { injectEndpoints } from '../api';
import { PicklistAdditionalNodeConfig, PicklistAdditionalNodeConfigParams } from './types';

const picklistApi = injectEndpoints({
  endpoints: (builder) => ({
    getAdditionalConfig: builder.query<PicklistAdditionalNodeConfig, PicklistAdditionalNodeConfigParams>({
      query: (configParams) => ({
        url: DataUrlConstants.NODE_CONFIG_ADDITIONAL_CONFIG,
        method: 'POST',
        body: configParams,
      }),
    }),
  }),
});

export const { useLazyGetAdditionalConfigQuery, util: pipelineErrorApiUtil } = picklistApi;
