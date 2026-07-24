//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import DataUrlConstants from 'utils/DataUrlConstants';

import { injectEndpoints } from '../api';
import { SyncariSdkInfo } from './types';

const connectorMetaApi = injectEndpoints({
  endpoints: (builder) => ({
    getSyncariSdkInfo: builder.query<SyncariSdkInfo, void>({
      query: () => {
        return {
          url: DataUrlConstants.PYPI_SYNCARI_SDK_INFO,
          responseHandler: (response) => response.json(),
        };
      },
    }),
  }),
});

export const { useGetSyncariSdkInfoQuery } = connectorMetaApi;
