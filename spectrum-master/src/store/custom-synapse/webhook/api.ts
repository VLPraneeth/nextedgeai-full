//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { CustomSynapse } from 'components/custom-synapse/types';
import { SupportedAuthType } from 'store/credential/types';
import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { injectEndpoints, tags } from '../../api';
import { WebhookLogsParams, WebhookLogsResponse, WebhookTestingResponse } from '../types';

const wehbookCustomSynapseApi = injectEndpoints({
  endpoints: (builder) => ({
    getWebhookCustomSypapseAuthtypes: builder.query<SupportedAuthType[] | undefined, void>({
      query: () => ({ url: DataUrlConstants.WEBHOOK_CUSTOM_SYNAPSE_AUTHTYPES }),
    }),
    getWebhookCustomSypapseEndpointUrl: builder.query<{ endpoint: string }, void>({
      query: () => ({ url: DataUrlConstants.WEBHOOK_CUSTOM_SYNAPSE_ENDPOINT_URL }),
    }),
    webhookCustomSypapseTest: builder.mutation<WebhookTestingResponse, Partial<CustomSynapse>>({
      query: (params) => {
        return {
          url: DataUrlConstants.WEBHOOK_CUSTOM_SYNAPSE_TEST,
          method: 'POST',
          body: params,
        };
      },
    }),
    createDraftWebhookCustomSynapse: builder.mutation<CustomSynapse, string>({
      query: (id) => {
        return {
          url: makeUrl(DataUrlConstants.WEBHOOK_CUSTOM_SYNAPSE_CREATE_DRAFT, { metadataId: id }),
          method: 'POST',
        };
      },
      invalidatesTags: [tags.AllCustomSynapseList],
    }),
    discardDraftWebhookCustomSynapse: builder.mutation<null, string>({
      query: (id) => {
        return {
          url: makeUrl(DataUrlConstants.WEBHOOK_CUSTOM_SYNAPSE_DISCARD_DRAFT, { metadataId: id }),
          method: 'POST',
        };
      },
      invalidatesTags: [tags.AllCustomSynapseList],
    }),
    publishWebhookCustomSynapse: builder.mutation<CustomSynapse, string>({
      query: (id) => {
        return {
          url: makeUrl(DataUrlConstants.WEBHOOK_CUSTOM_SYNAPSE_PUBLISH, { metadataId: id }),
          method: 'POST',
        };
      },
      invalidatesTags: [tags.AllCustomSynapseList],
    }),
    getWebhookLogs: builder.mutation<WebhookLogsResponse, WebhookLogsParams>({
      query: ({ cursor, direction, count, connectorId }) => {
        return {
          url: makeUrl(
            DataUrlConstants.WEBHOOK_CUSTOM_SYNAPSE_LOGS,
            {},
            {
              cursor,
              direction,
              count,
              connectorId,
            }
          ),
          method: 'POST',
        };
      },
    }),
    getWebhookCustomSypapseHttpCodes: builder.query<{ name: string; value: number }[], void>({
      query: () => ({ url: DataUrlConstants.WEBHOOK_CUSTOM_SYNAPSE_HTTP_CODES }),
    }),
  }),
});

export const {
  useGetWebhookCustomSypapseAuthtypesQuery,
  useCreateDraftWebhookCustomSynapseMutation,
  useDiscardDraftWebhookCustomSynapseMutation,
  usePublishWebhookCustomSynapseMutation,
  useWebhookCustomSypapseTestMutation,
  useLazyGetWebhookCustomSypapseEndpointUrlQuery,
  useGetWebhookCustomSypapseEndpointUrlQuery,
  useGetWebhookLogsMutation,
  useGetWebhookCustomSypapseHttpCodesQuery,
} = wehbookCustomSynapseApi;
