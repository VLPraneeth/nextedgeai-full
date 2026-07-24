//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { injectEndpoints, tags } from '../api';
import { Credential, CredentialMetadata, CredentialRequest } from './types';

const credentialApi = injectEndpoints({
  endpoints: (builder) => ({
    // Get the list of credential metadata
    getCredentialMetadataList: builder.query<CredentialMetadata[], void>({
      query: () => ({ url: DataUrlConstants.CREDENTIAL_DESC }),
      providesTags: (result) => [
        ...(result || []).map((credentialMetadata: CredentialMetadata) =>
          tags.CredentialMetadata(credentialMetadata.id)
        ),
        tags.CredentialMetadataList,
      ],
    }),

    // Get the list of credentail
    getCredentialList: builder.query<Credential[], void>({
      query: () => ({ url: DataUrlConstants.CREDENTIAL }),
      providesTags: (result) => [
        ...(result || []).map((credential: Credential) => tags.Credential(credential.id)),
        tags.CredentialList,
      ],
    }),
    // Save credential
    saveCredential: builder.mutation<Credential, CredentialRequest>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.CREDENTIAL),
          method: 'POST',
          body: params,
        };
      },
      invalidatesTags: [tags.CredentialList],
    }),
    // Delete credential
    deleteCredential: builder.mutation<Credential, Credential>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.CREDENTIAL_ITEM, (params as unknown) as Record<string, string>),
          method: 'DELETE',
        };
      },
      invalidatesTags: [tags.CredentialList],
    }),
  }),
});

export const {
  useGetCredentialMetadataListQuery,
  useGetCredentialListQuery,
  useSaveCredentialMutation,
  useDeleteCredentialMutation,
} = credentialApi;
