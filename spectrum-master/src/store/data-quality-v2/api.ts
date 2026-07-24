//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { injectEndpoints, tags } from '../api';
import {
  DfiV2CategoryPayload,
  DfiV2CategoryUpdate,
  DfiV2Rule,
  DfiV2RulePayload,
  RulesMetadata,
  DfiProvisionStatus,
} from './types';

const dataQualityV2Api = injectEndpoints({
  endpoints: (builder) => ({
    getCategoriesList: builder.query<DfiV2CategoryPayload[] | undefined, void>({
      query: () => ({ url: DataUrlConstants.CATEGORIES }),
      providesTags: (result) => [
        ...(result || []).map((rule: any) => tags.CustomAction(rule.id)),
        tags.DfiV2CategoriesList,
      ],
    }),
    saveCategories: builder.mutation<DfiV2CategoryPayload[], { categories: Partial<DfiV2CategoryUpdate>[] }>({
      query: ({ categories }) => {
        return {
          url: DataUrlConstants.CATEGORIES,
          method: 'POST',
          body: categories,
        };
      },
      invalidatesTags: [tags.DfiV2CategoriesList],
    }),
    deleteCategory: builder.mutation<void, { categoryId: string }>({
      query: ({ categoryId }) => {
        return {
          url: makeUrl(DataUrlConstants.CATEGORY, { categoryId }),
          method: 'DELETE',
        };
      },
      invalidatesTags: [tags.DfiV2CategoriesList],
    }),
    getRulesMetadata: builder.query<RulesMetadata, { syncariEntityId: string }>({
      query: (params) => ({ url: makeUrl(DataUrlConstants.RULES_METADATA, params) }),
    }),
    getRulesList: builder.query<DfiV2RulePayload[] | undefined, { syncariEntityId: string; version: string }>({
      query: (params) => ({ url: makeUrl(DataUrlConstants.RULES, params) }),
      providesTags: (result) => [...(result || []).map((rule: any) => tags.DfiV2Rules(rule.id)), tags.DfiV2RulesList],
    }),
    saveRule: builder.mutation<
      DfiV2RulePayload[],
      { rule: Partial<DfiV2Rule>; syncariEntityId: string; version: string }
    >({
      query: ({ rule, syncariEntityId, version }) => {
        return {
          url: makeUrl(DataUrlConstants.RULES, { syncariEntityId, version }),
          method: 'POST',
          body: rule,
        };
      },
      invalidatesTags: [tags.DfiV2RulesList],
    }),
    deleteRule: builder.mutation<void, { ruleId: string; syncariEntityId: string; version: string }>({
      query: ({ ruleId, syncariEntityId, version }) => {
        return {
          url: makeUrl(DataUrlConstants.RULE, { syncariEntityId, version, ruleId }),
          method: 'DELETE',
        };
      },
      invalidatesTags: [tags.DfiV2RulesList],
    }),
    getDFIProvisionStatus: builder.query<DfiProvisionStatus, { syncariEntityId: string; draftStatus: string }>({
      query: (params) => ({ url: makeUrl(DataUrlConstants.DFI_PROVISION_STATUS, params) }),
      providesTags: (result, error, { syncariEntityId }) => [
        { type: 'DfiProvisionStatus', id: syncariEntityId },
        { type: 'DfiProvisionStatus', id: 'LIST' },
      ],
    }),
    patchPipelineSettings: builder.mutation<void, { entityId: string; payload: any }>({
      query: ({ entityId, payload }) => {
        return {
          url: makeUrl(DataUrlConstants.ENTITY_PIPELINE, { entityId }),
          method: 'PATCH',
          body: payload,
        };
      },
      invalidatesTags: (_, __, { entityId }) => [
        { type: 'DfiProvisionStatus', id: entityId },
        { type: 'DfiProvisionStatus', id: 'LIST' },
      ],
    }),
    getReferenceDataSets: builder.query<{ referenceDataSets: any[] }, void>({
      query: () => ({ url: DataUrlConstants.REFERENCE_DATA_SETS }),
    }),
  }),
});

export const {
  useGetCategoriesListQuery,
  useSaveCategoriesMutation,
  useDeleteCategoryMutation,
  useGetRulesMetadataQuery,
  useGetRulesListQuery,
  useSaveRuleMutation,
  useDeleteRuleMutation,
  useGetDFIProvisionStatusQuery,
  usePatchPipelineSettingsMutation,
  useGetReferenceDataSetsQuery,
} = dataQualityV2Api;
