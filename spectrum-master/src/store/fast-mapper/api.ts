//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { injectEndpoints } from '../api';
import { ServerMapping } from './types';

const fastMapperApi = injectEndpoints({
  endpoints: (builder) => ({
    autoMapFields: builder.mutation<
      ServerMapping[],
      { syncariEntityId: string; sourceEntityId: string; autoCreateUnmappedFields: boolean; mapperType: string }
    >({
      query: ({ syncariEntityId, sourceEntityId, autoCreateUnmappedFields, mapperType }) => {
        return {
          method: 'POST',
          url: makeUrl(
            DataUrlConstants.ENTITY_AUTO_MAP,
            { syncariEntityId, sourceEntityId, mapperType },
            { autoCreateUnmappedFields }
          ),
        };
      },
    }),
  }),
});

export const { useAutoMapFieldsMutation } = fastMapperApi;
