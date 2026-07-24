//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { CustomSynapse } from 'components/custom-synapse/types';
import { Connector } from 'reducers/connectorReducer';
import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl, MakeUrlTokens } from 'utils/UrlUtil';

import { injectEndpoints, tags } from '../../api';
import { SDKCustomSynapseFunctionDeployStatuses, CustomSynapsePayload, TestConnectionResponse } from '../types';

const customSynapseApi = injectEndpoints({
  endpoints: (builder) => ({
    // Get the list of all custom synapses the user have authoring access
    getAllCustomSynapseList: builder.query<CustomSynapse[] | undefined, void>({
      query: () => ({ url: DataUrlConstants.CUSTOM_SYNAPSE_ALL }),
      providesTags: (result) => [
        ...(result || []).map((customSynapse: any) => tags.CustomSynapse(customSynapse.id)),
        tags.AllCustomSynapseList,
      ],
    }),
    // Used for both sdk and http synapse
    getCustomSynapseItem: builder.query<CustomSynapse | undefined, Record<string, string | undefined>>({
      query: (params) => ({ url: makeUrl(DataUrlConstants.CUSTOM_SYNAPSE_ITEM, params) }),
      providesTags: (result) => [...(result?.id ? [tags.CustomSynapse(result?.id)] : [])],
    }),
    // Fetch the current status of the associated cloud function
    getCustomSynapseStatus: builder.query<
      {
        code: SDKCustomSynapseFunctionDeployStatuses;
        errorStatusMessage: string;
      },
      { connectorMetaDefinitionId: string }
    >({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.SDK_CUSTOM_SYNAPSE_STATUS, params),
          method: 'GET',
        };
      },
    }),
    // Save custom synapse
    saveCustomSynapse: builder.mutation<CustomSynapsePayload, CustomSynapsePayload>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.SDK_CUSTOM_SYNAPSE, (params as unknown) as MakeUrlTokens),
          method: 'POST',
          body: params,
        };
      },
      invalidatesTags: [tags.AllCustomSynapseList],
    }),
    // Test SDK authentication
    testCustomSynapse: builder.mutation<TestConnectionResponse, Partial<Connector>>({
      query: (params) => {
        return {
          url: DataUrlConstants.SDK_CUSTOM_SYNAPSE_TEST,
          method: 'POST',
          body: params,
        };
      },
    }),
    // Delete custom synapse sdk and http
    deleteCustomSynapse: builder.mutation<CustomSynapsePayload, Record<string, string>>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.CUSTOM_SYNAPSE_ITEM, params),
          method: 'DELETE',
        };
      },
      invalidatesTags: [tags.AllCustomSynapseList],
    }),
    // Discard custom synapse draft
    discardCustomSynapseDraft: builder.mutation<CustomSynapsePayload, Record<string, string>>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.SDK_CUSTOM_SYNAPSE_DISCARD_DRAFT, params),
          method: 'POST',
        };
      },
      invalidatesTags: [tags.AllCustomSynapseList],
    }),
    // Create a draft of the custom synapse
    createCustomSynapseDraft: builder.mutation<CustomSynapsePayload, Record<string, string>>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.SDK_CUSTOM_SYNAPSE_CREATE_DRAFT, params),
          method: 'POST',
        };
      },
      invalidatesTags: [tags.AllCustomSynapseList],
    }),
    // Get the custom synapse draft
    getCustomSynapseDraft: builder.query<CustomSynapsePayload, Record<string, string>>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.SDK_CUSTOM_SYNAPSE_GET_DRAFT, params),
          method: 'GET',
        };
      },
      providesTags: (result) => [...(result?.id ? [tags.CustomSynapse(result.id)] : [])],
    }),
    // Request approval for custom synapse
    submitForApprovalCustomSynapse: builder.mutation<CustomSynapsePayload, Record<string, string>>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.SDK_CUSTOM_SYNAPSE_SUBMIT_FOR_APPROVAL, params),
          method: 'POST',
        };
      },
      invalidatesTags: [tags.AllCustomSynapseList],
    }),
    // Withdraw request for approval
    withdrawApprovalCustomSynapse: builder.mutation<CustomSynapsePayload, Record<string, string>>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.SDK_CUSTOM_SYNAPSE_WITHDRAW_APPROVAL, params),
          method: 'POST',
        };
      },
      invalidatesTags: [tags.AllCustomSynapseList],
    }),
    // Publish custom synapse
    approveCustomSynapse: builder.mutation<CustomSynapsePayload, Record<string, string>>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.SDK_CUSTOM_SYNAPSE_APPROVE, params),
          method: 'POST',
        };
      },
      invalidatesTags: [tags.AllCustomSynapseList],
    }),
  }),
});

export const {
  useApproveCustomSynapseMutation,
  useCreateCustomSynapseDraftMutation,
  useDeleteCustomSynapseMutation,
  useDiscardCustomSynapseDraftMutation,
  useGetCustomSynapseDraftQuery,
  useLazyGetCustomSynapseStatusQuery,
  useSaveCustomSynapseMutation,
  useSubmitForApprovalCustomSynapseMutation,
  useTestCustomSynapseMutation,
  useWithdrawApprovalCustomSynapseMutation,
  useGetAllCustomSynapseListQuery,
  useGetCustomSynapseItemQuery,
} = customSynapseApi;
