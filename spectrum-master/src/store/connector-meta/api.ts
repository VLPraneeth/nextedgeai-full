//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { injectEndpoints } from '../api';
import { ConnectorDefaultMapping } from './types';

const connectorMetaApi = injectEndpoints({
  endpoints: (builder) => ({
    getCapabilities: builder.query<string | undefined, { metaId: string }>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.SYNAPSE_CAPABILITIES_DOC, params),
          responseHandler: (response) => response.text(),
        };
      },
    }),
    getDefaultMappings: builder.query<ConnectorDefaultMapping[], { metaId: string }>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.SYNAPSE_DEFAULT_MAPPINGS, params),
        };
      },
    }),
  }),
});

export const { useGetCapabilitiesQuery, useGetDefaultMappingsQuery } = connectorMetaApi;
