//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { injectEndpoints, tags } from '../api';
import { EntityMetricsPayload } from './types';

const pipelineVisibilityApi = injectEndpoints({
  endpoints: (builder) => ({
    // Get entity sync metrics
    getEntitySyncMetrics: builder.query<EntityMetricsPayload, { syncariEntityId: string }>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.ENTITY_SYNC_METRICS, params),
          method: 'GET',
        };
      },
      providesTags: (result) => [...(result?.syncariEntityId ? [tags.EntityDetails(result.syncariEntityId)] : [])],
    }),
  }),
});

export const { useLazyGetEntitySyncMetricsQuery } = pipelineVisibilityApi;
