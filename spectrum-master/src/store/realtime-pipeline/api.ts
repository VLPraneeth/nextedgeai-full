//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { injectEndpoints, tags } from '../api';
import { RealtimePipelineIpWhitelist } from './types';

const realtimePipeline = injectEndpoints({
  endpoints: (builder) => ({
    getRealtimePipelineIpWhite: builder.query<RealtimePipelineIpWhitelist, void>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.REALTIME_PIPELINE),
          method: 'GET',
        };
      },
      providesTags: () => [tags.RealtimePipelineIpWhitelistList],
    }),
    setRealtimePipelineIpWhite: builder.mutation<RealtimePipelineIpWhitelist, RealtimePipelineIpWhitelist>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.REALTIME_PIPELINE),
          body: params,
          method: 'POST',
        };
      },
      invalidatesTags: [tags.RealtimePipelineIpWhitelistList],
    }),
  }),
});

export const { useGetRealtimePipelineIpWhiteQuery, useSetRealtimePipelineIpWhiteMutation } = realtimePipeline;
