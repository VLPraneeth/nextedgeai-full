//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { GraphModel } from 'store/pipeline/types';
import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { injectEndpoints, tags } from '../api';

export interface CloneEntityPayload {
  entityId: string;
  cloneFromDraft: boolean;
  apiName?: string;
  displayName?: string;
  dataStoreName?: string;
  description?: string;
  tags?: string[];
}

const datastoreApi = injectEndpoints({
  endpoints: (builder) => ({
    clonePipeline: builder.mutation<GraphModel, CloneEntityPayload>({
      query: ({ entityId, ...body }) => {
        return {
          url: makeUrl(DataUrlConstants.CLONE_ENTITY, { entityId }),
          method: 'POST',
          body: { tags: [], ...body },
        };
      },
      invalidatesTags: [tags.DataStoreList],
    }),
  }),
});

export const { useClonePipelineMutation } = datastoreApi;
