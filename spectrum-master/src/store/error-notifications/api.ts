//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import DataUrlConstants from 'utils/DataUrlConstants';

import { injectEndpoints } from '../api';
import { ErrorCatalogMetadata, ErrorNotificationPayload } from './types';

const customSynapseApi = injectEndpoints({
  endpoints: (builder) => ({
    // Fetch the error catalog metadata
    getNotificationsMetadata: builder.query<ErrorCatalogMetadata, void>({
      query: (params) => {
        return {
          url: DataUrlConstants.ERROR_CATALAG,
          method: 'GET',
        };
      },
    }),
    saveErrorNotifications: builder.mutation<Error, ErrorNotificationPayload>({
      query: (params) => {
        return {
          url: DataUrlConstants.ERROR_NOTIFICATION,
          method: 'POST',
          body: params,
        };
      },
    }),
  }),
});

export const { useGetNotificationsMetadataQuery, useSaveErrorNotificationsMutation } = customSynapseApi;
