//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { injectEndpoints } from '../api';
import { PipelineError } from './types';

const pipelineErrorApi = injectEndpoints({
  endpoints: (builder) => ({
    getPipelineError: builder.query<PipelineError, { entityId: string }>({
      query: ({ entityId }) => ({
        url: makeUrl(DataUrlConstants.ENTITY_PIPELINE_ERROR, { entityId }),
      }),
    }),
  }),
});

export const { useGetPipelineErrorQuery, util: pipelineErrorApiUtil } = pipelineErrorApi;
