//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl, MakeUrlTokens } from 'utils/UrlUtil';

import { injectEndpoints, tags } from '../api';
import { CustomActionPayload } from './types';
import { transformToCustomAction, transformToCustomActions } from './util';

const customActionApi = injectEndpoints({
  endpoints: (builder) => ({
    // Get the list of custom action the user have authoring access
    getCustomActionList: builder.query<CustomActionPayload[] | undefined, void>({
      query: () => ({ url: DataUrlConstants.CUSTOM_ACTION_LIST }),
      providesTags: (result) => [
        ...(result || []).map((customAction: any) => tags.CustomAction(customAction.id)),
        tags.CustomActionList,
      ],
      transformResponse: transformToCustomActions,
    }),
    // Save custom action
    saveCustomAction: builder.mutation<CustomActionPayload, CustomActionPayload>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.CUSTOM_ACTION, (params as unknown) as MakeUrlTokens),
          method: 'POST',
          body: params,
        };
      },
      transformResponse: transformToCustomAction,
      invalidatesTags: [tags.CustomActionList],
    }),
    // Delete custom action
    deleteCustomAction: builder.mutation<CustomActionPayload, Record<string, string>>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.CUSTOM_ACTION_ITEM, params),
          method: 'DELETE',
        };
      },
      transformResponse: transformToCustomAction,
      invalidatesTags: [tags.CustomActionList],
    }),
    // Discard custom action draft
    discardCustomActionDraft: builder.mutation<CustomActionPayload, Record<string, string>>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.CUSTOM_ACTION_DISCARD_DRAFT, params),
          method: 'POST',
        };
      },
      transformResponse: transformToCustomAction,
      invalidatesTags: [tags.CustomActionList],
    }),
    // Create a draft of the custom action
    createCustomActionDraft: builder.mutation<CustomActionPayload, Record<string, string>>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.CUSTOM_ACTION_CREATE_DRAFT, params),
          method: 'POST',
        };
      },
      transformResponse: transformToCustomAction,
      invalidatesTags: [tags.CustomActionList],
    }),
    // Get the custom action draft
    getCustomActionDraft: builder.query<CustomActionPayload, Record<string, string>>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.CUSTOM_ACTION_GET_DRAFT, params),
          method: 'GET',
        };
      },
      transformResponse: transformToCustomAction,
      providesTags: (result) => [...(result?.id ? [tags.CustomAction(result.id)] : [])],
    }),
    // Publish custom action
    publishCustomAction: builder.mutation<CustomActionPayload, Record<string, any>>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.CUSTOM_ACTION_PUBLISH, params),
          method: 'POST',
        };
      },
      transformResponse: transformToCustomAction,
      invalidatesTags: [tags.CustomActionList],
    }),
    // Share custom action
    shareCustomAction: builder.mutation<CustomActionPayload, Record<string, any>>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.CUSTOM_ACTION_SHARE, params),
          method: 'POST',
        };
      },
      transformResponse: transformToCustomAction,
      invalidatesTags: [tags.CustomActionList],
    }),
  }),
});

export const {
  useCreateCustomActionDraftMutation,
  useDeleteCustomActionMutation,
  useDiscardCustomActionDraftMutation,
  useGetCustomActionListQuery,
  useLazyGetCustomActionDraftQuery,
  usePublishCustomActionMutation,
  useSaveCustomActionMutation,
  useShareCustomActionMutation,
} = customActionApi;
