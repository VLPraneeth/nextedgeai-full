//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Connector, ConnectorMetadata } from 'reducers/connectorReducer';
import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl, replaceToken } from 'utils/UrlUtil';

import { injectEndpoints, tags } from '../api';
import { DataStoreConfig } from './types';

export interface DataStoreLag {
  dataStoreCurrentTimestamp: string;
  entityId: string;
  entityName: string;
  pendingRecords: number;
}

const datastoreApi = injectEndpoints({
  endpoints: (builder) => ({
    getDataStoreLag: builder.query<DataStoreLag[], void>({
      query: () => ({
        url: makeUrl(DataUrlConstants.DATA_STORE_LAG),
      }),
    }),
    getDataStore: builder.query<any, void>({
      query: () => ({
        url: makeUrl(DataUrlConstants.GET_DATA_STORE),
      }),
    }),
    // Get the list of configured data stores
    getDataStoresList: builder.query<DataStoreConfig[] | undefined, void>({
      query: () => ({ url: DataUrlConstants.GET_DATA_STORE_LIST }),
      providesTags: [tags.DataStoreList],
    }),
    // Get the list of configured data stores
    getDataStoreDescribe: builder.query<ConnectorMetadata[] | undefined, void>({
      query: () => ({ url: DataUrlConstants.GET_DATA_STORE_DESCRIBE }),
    }),
    createConnection: builder.mutation<Connector, Partial<Connector>>({
      query: (connector) => {
        return {
          url: makeUrl(DataUrlConstants.GET_DATA_STORE),
          method: 'POST',
          body: connector,
        };
      },
      invalidatesTags: [tags.DataStoreList],
    }),
    provisionSyncariDataStore: builder.mutation<Connector, void>({
      query: () => ({
        url: makeUrl(DataUrlConstants.PROVISION_DATA_STORE),
        method: 'POST',
      }),
      invalidatesTags: [tags.DataStoreList],
    }),
    activateConnection: builder.mutation<DataStoreConfig, string>({
      query: (dataStoreId: string) => {
        return {
          url: makeUrl(DataUrlConstants.ACTIVATE_DATA_STORE, { dataStoreId }),
          method: 'PATCH',
        };
      },
      invalidatesTags: [tags.DataStoreList],
    }),
    deactivateConnection: builder.mutation<DataStoreConfig, string>({
      query: (dataStoreId: string) => {
        return {
          url: makeUrl(DataUrlConstants.DEACTIVATE_DATA_STORE, { dataStoreId }),
          method: 'PATCH',
        };
      },
      invalidatesTags: [tags.DataStoreList],
    }),
    updateConnection: builder.mutation<DataStoreConfig, DataStoreConfig>({
      query: (config) => {
        return {
          url: makeUrl(DataUrlConstants.UPDATE_DATA_STORE, { dataStoreId: config.id }),
          method: 'PUT',
          body: config,
        };
      },
      invalidatesTags: [tags.DataStoreList],
    }),
    deleteConnection: builder.mutation<null, string>({
      query: (dataStoreId) => {
        return {
          url: makeUrl(DataUrlConstants.UPDATE_DATA_STORE, { dataStoreId }),
          method: 'DELETE',
        };
      },
      invalidatesTags: [tags.DataStoreList],
    }),
    createOauthRedirectUrlQuery: builder.mutation<Connector, Partial<Connector>>({
      query: (connector) => {
        return {
          url: makeUrl(DataUrlConstants.GET_DATA_STORE),
          method: 'POST',
          body: connector,
        };
      },
    }),
    getConnectorInfo: builder.mutation<DataStoreConfig[] | undefined, void>({
      query: () => {
        return {
          url: makeUrl(DataUrlConstants.GET_DATA_STORE),
          method: 'GET',
        };
      },
    }),
    updateDatastoreConnection: builder.mutation<DataStoreConfig, DataStoreConfig>({
      query: (config) => {
        return {
          url: makeUrl(DataUrlConstants.UPDATE_DATA_STORE, { dataStoreId: config.id }),
          method: 'PUT',
          body: config,
        };
      },
    }),
    oAuthenticateDatastore: builder.mutation<{ location: string }, string>({
      query: (id) => {
        return {
          url: replaceToken(DataUrlConstants.OAUTH_INITIATE, { connectorId: id }),
          method: 'GET',
        };
      },
    }),
  }),
});

export const {
  useActivateConnectionMutation,
  useCreateConnectionMutation,
  useDeactivateConnectionMutation,
  useDeleteConnectionMutation,
  useGetDataStoreDescribeQuery,
  useGetDataStoreLagQuery,
  useGetDataStoreQuery,
  useGetDataStoresListQuery,
  useLazyGetDataStoreLagQuery,
  useLazyGetDataStoreQuery,
  useProvisionSyncariDataStoreMutation,
  useUpdateConnectionMutation,
  useUpdateDatastoreConnectionMutation,
  useCreateOauthRedirectUrlQueryMutation,
  useOAuthenticateDatastoreMutation,
  useGetConnectorInfoMutation,
} = datastoreApi;
