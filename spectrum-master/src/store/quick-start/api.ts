//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { SkullConfig } from 'components/skull';
import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { injectEndpoints, tags } from '../api';
import {
  QuickStart,
  QuickStartDynamicStep,
  QuickStartInstall,
  QuickStartInstalls,
  QuickStartInstances,
  QuickStarts,
} from './types';

const quickStartApi = injectEndpoints({
  endpoints: (builder) => ({
    // Get the list of quick the user have authoring access
    getQuickStartAuthorList: builder.query<QuickStarts, void>({
      query: () => ({ url: DataUrlConstants.QUICK_START_AUTHOR_LIST }),
      providesTags: (result) => [
        ...(result || []).map((quickStart) => tags.QuickStartAuthor(quickStart.id)),
        tags.QuickStartAuthorList,
      ],
    }),

    // Get the skull config to author a quick start
    getQuickStartAuthorConfig: builder.query<SkullConfig, void>({
      query: () => ({ url: DataUrlConstants.QUICK_START_AUTHOR_CONFIG }),
    }),

    // Get the dynamic inputs and steps for a given step
    getDynamicStep: builder.mutation<QuickStartDynamicStep, QuickStartDynamicStep>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.QUICK_START_DYNAMIC_STEP, params),
          method: 'POST',
          body: params,
        };
      },
    }),

    // Create a quick start
    createQuickStart: builder.mutation<QuickStartDynamicStep, QuickStartDynamicStep>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.QUICK_START_CREATE_QUICK_START, params),
          method: 'POST',
          body: params,
        };
      },
      invalidatesTags: [tags.QuickStartAuthorList, tags.QuickStartMarketplaceList],
    }),

    // Update a quick start
    updateQuickStart: builder.mutation<QuickStartDynamicStep, QuickStartDynamicStep>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.QUICK_START_BY_ID, params),
          method: 'PUT',
          body: params,
        };
      },
      // TODO: Invalidate single quickstart
      invalidatesTags: [tags.QuickStartAuthorList, tags.QuickStartMarketplaceList],
    }),

    // Delete a quick start
    deleteQuickStart: builder.mutation<QuickStart, QuickStartDynamicStep>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.QUICK_START_BY_ID, params),
          method: 'DELETE',
        };
      },
      invalidatesTags: (result) =>
        result
          ? [
              tags.QuickStartAuthor(result.id),
              tags.QuickStartMarketplace(result.id),
              tags.QuickStartShared(result.id),
              tags.QuickStartAuthorList,
              tags.QuickStartMarketplaceList,
            ]
          : [tags.QuickStartAuthorList, tags.QuickStartMarketplaceList],
    }),

    // Create a draft of the quick start
    createQuickStartDraft: builder.mutation<QuickStart, QuickStartDynamicStep>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.QUICK_START_CREATE_DRAFT, params),
          method: 'POST',
        };
      },
      invalidatesTags: [tags.QuickStartAuthorList],
    }),

    // Discard the draft of a quick start
    discardQuickStartDraft: builder.mutation<QuickStartDynamicStep, QuickStartDynamicStep>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.QUICK_START_DISCARD_DRAFT, params),
          method: 'POST',
        };
      },
      invalidatesTags: (result) => [
        ...(result ? [result] : []).map((quickStart) => tags.QuickStartAuthor(quickStart.id)),
        tags.QuickStartAuthorList,
      ],
    }),

    // Approve the draft of a quick start
    approveQuickStartDraft: builder.mutation<QuickStartDynamicStep, QuickStartDynamicStep>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.QUICK_START_APPROVE_DRAFT, params),
          method: 'POST',
        };
      },
      // TODO: Invalidate a single quick start
      invalidatesTags: [tags.QuickStartAuthorList, tags.QuickStartMarketplaceList],
    }),

    // Get the quick start draft
    getQuickStartDraft: builder.query<QuickStart, Record<string, string>>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.QUICK_START_GET_DRAFT, params),
          method: 'GET',
        };
      },
      providesTags: (result) => [...(result ? [tags.QuickStartAuthor(result.id)] : [])],
    }),

    // Get the approved quick start
    getQuickStartApproved: builder.query<QuickStart, Record<string, string>>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.QUICK_START_GET_APPROVED, params),
          method: 'GET',
        };
      },
      providesTags: (result) => [...(result ? [tags.QuickStartAuthor(result.id)] : [])],
    }),

    // Get the marketplace quick starts
    getQuickStartMarketplaceList: builder.query<QuickStartInstalls, Record<string, string>>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.QUICK_START_MARKETPLACE_LIST, params),
          method: 'GET',
        };
      },
      providesTags: (result) => [
        ...(result || []).map((quickStart) => tags.QuickStartMarketplace(quickStart.id)),
        tags.QuickStartMarketplaceList,
      ],
    }),

    // Get the shared quick starts
    getQuickStartSharedList: builder.query<QuickStartInstalls, Record<string, string>>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.QUICK_START_SHARED_LIST, params),
          method: 'GET',
        };
      },
      providesTags: (result) => [
        ...(result || []).map((quickStart) => tags.QuickStartShared(quickStart.id)),
        tags.QuickStartSharedList,
      ],
    }),

    // Get the quick start the can be installed. Quick start with Skull config
    runQuickStart: builder.mutation<QuickStartInstall, Record<string, string>>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.QUICK_START_GET_INSTALL, params),
          method: 'GET',
        };
      },
      invalidatesTags: [tags.QuickStartMarketplaceList, tags.QuickStartSharedList],
    }),

    // Get the dynamic inputs and steps for a given step
    getInstallDynamicStep: builder.mutation<QuickStartDynamicStep, QuickStartDynamicStep>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.QUICK_START_INSTALL_DYNAMIC_STEP, params),
          method: 'POST',
          body: params,
        };
      },
    }),

    // Install a quick start
    installQuickStart: builder.mutation<QuickStartInstall, QuickStartDynamicStep>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.QUICK_START_INSTALL, params),
          method: 'POST',
          body: params,
        };
      },
      invalidatesTags: [tags.QuickStartMarketplaceList],
    }),

    // Cancel a quick start install
    cancelInstallQuickStart: builder.mutation<QuickStartInstall, QuickStartDynamicStep>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.QUICK_START_INSTALL_CANCEL, params),
          method: 'POST',
          body: params,
        };
      },
      invalidatesTags: [tags.QuickStartMarketplaceList, tags.QuickStartSharedList],
    }),

    // Get the list of instance user has access to
    getAuthorAvailableInstances: builder.query<QuickStartInstances, void>({
      query: () => ({ url: DataUrlConstants.QUICK_START_AUTHOR_INSTANCES }),
      providesTags: (result) => [
        ...(result || []).map((instance) => tags.QuickStartInstance(instance.value)),
        tags.QuickStartInstanceList,
      ],
    }),

    // Submit the publish options
    publishQuickStart: builder.mutation<void, QuickStartDynamicStep>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.QUICK_START_AUTHOR_PUBLISH, params),
          method: 'POST',
          body: params,
        };
      },
      invalidatesTags: [tags.QuickStartMarketplaceList, tags.QuickStartAuthorList],
    }),
  }),
  overrideExisting: false,
});

export const {
  useGetQuickStartAuthorListQuery,
  useGetQuickStartAuthorConfigQuery,
  useGetDynamicStepMutation,
  useCreateQuickStartMutation,
  useUpdateQuickStartMutation,
  useDeleteQuickStartMutation,
  useCreateQuickStartDraftMutation,
  useDiscardQuickStartDraftMutation,
  useApproveQuickStartDraftMutation,
  useGetQuickStartDraftQuery,
  useLazyGetQuickStartDraftQuery,
  useGetQuickStartApprovedQuery,
  useLazyGetQuickStartApprovedQuery,
  useGetQuickStartMarketplaceListQuery,
  useGetQuickStartSharedListQuery,
  useRunQuickStartMutation,
  useGetInstallDynamicStepMutation,
  useInstallQuickStartMutation,
  useCancelInstallQuickStartMutation,
  useGetAuthorAvailableInstancesQuery,
  usePublishQuickStartMutation,
} = quickStartApi;
