//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { injectEndpoints, tags } from '../../api';
import {
  AbacAttributeRequest,
  AbacAttributeResponseDTO,
  AbacAttributeValueDTO,
  AbacPolicyRequest,
  AbacPolicyResponseDTO,
  AbacResource,
  KeyValue,
  ResourceType,
} from './types';

const abacApi = injectEndpoints({
  endpoints: (builder) => ({
    // Attribute endpoints
    listAttributes: builder.query<AbacAttributeResponseDTO[], void>({
      query: () => ({ url: DataUrlConstants.ABAC_ATTRIBUTE }),
      providesTags: [tags.AbacAttributeList],
    }),
    getAttribute: builder.query<AbacAttributeRequest, string>({
      query: (id) => ({ url: makeUrl(DataUrlConstants.ABAC_ATTRIBUTE_ITEM, { id }) }),
    }),
    addAttribute: builder.mutation<AbacAttributeResponseDTO, AbacAttributeRequest>({
      query: (req) => ({
        url: DataUrlConstants.ABAC_ATTRIBUTE,
        method: 'POST',
        body: req,
      }),
      invalidatesTags: [tags.AbacAttributeList],
    }),
    editAttributes: builder.mutation<AbacAttributeResponseDTO, { id: string; req: AbacAttributeRequest }>({
      query: ({ id, req }) => ({
        url: makeUrl(DataUrlConstants.ABAC_ATTRIBUTE_ITEM, { id }),
        method: 'PUT',
        body: req,
      }),
      invalidatesTags: [tags.AbacAttributeList],
    }),
    deleteAttribute: builder.mutation<void, string>({
      query: (id) => ({
        url: makeUrl(DataUrlConstants.ABAC_ATTRIBUTE_ITEM, { id }),
        method: 'DELETE',
      }),
      invalidatesTags: [tags.AbacAttributeList],
    }),
    getUserAttribute: builder.query<AbacAttributeResponseDTO, void>({
      query: () => ({ url: DataUrlConstants.ABAC_ATTRIBUTE_USER }),
    }),
    getSupportedDataTypes: builder.query<{ label: string; value: string }[], void>({
      query: () => ({
        url: DataUrlConstants.ABAC_ATTRIBUTE_SUPPORTED_DATATYPES,
      }),
    }),

    // Attribute Value endpoints
    listAttributeValues: builder.query<AbacAttributeValueDTO[], void>({
      query: () => ({ url: DataUrlConstants.ABAC_ATTRIBUTE_VALUE }),
      providesTags: [tags.AbacValueList],
    }),
    getAttributeValue: builder.query<AbacAttributeValueDTO, string>({
      query: (id) => ({ url: makeUrl(DataUrlConstants.ABAC_ATTRIBUTE_VALUE_ITEM, { id }) }),
    }),
    addAttributeValue: builder.mutation<AbacAttributeValueDTO[], AbacAttributeValueDTO[]>({
      query: (req) => ({
        url: DataUrlConstants.ABAC_ATTRIBUTE_VALUE,
        method: 'POST',
        body: req,
      }),
      invalidatesTags: [tags.AbacValueList],
    }),
    deleteAttributeValue: builder.mutation<void, string>({
      query: (id) => ({
        url: makeUrl(DataUrlConstants.ABAC_ATTRIBUTE_VALUE_ITEM, { id }),
        method: 'DELETE',
      }),
      invalidatesTags: [tags.AbacValueList],
    }),
    deleteAttributeValues: builder.mutation<any, any>({
      query: (req) => ({
        url: DataUrlConstants.ABAC_ATTRIBUTE_VALUE,
        method: 'PATCH',
        body: req,
      }),
      invalidatesTags: [tags.AbacValueList],
    }),

    // Policy endpoints
    listPolicies: builder.query<AbacPolicyResponseDTO[], void>({
      query: () => ({ url: DataUrlConstants.ABAC_POLICY }),
      providesTags: [tags.AbacPolicyList],
    }),
    getPolicy: builder.query<AbacPolicyResponseDTO, string>({
      query: (id) => ({ url: makeUrl(DataUrlConstants.ABAC_POLICY_ITEM, { id }) }),
    }),
    addPolicy: builder.mutation<AbacPolicyResponseDTO, AbacPolicyRequest>({
      query: (req) => ({
        url: DataUrlConstants.ABAC_POLICY,
        method: 'POST',
        body: req,
      }),
      invalidatesTags: [tags.AbacPolicyList],
    }),
    editPolicy: builder.mutation<AbacPolicyResponseDTO, { id: string; req: AbacPolicyRequest }>({
      query: ({ id, req }) => ({
        url: makeUrl(DataUrlConstants.ABAC_POLICY_ITEM, { id }),
        method: 'PUT',
        body: req,
      }),
      invalidatesTags: [tags.AbacPolicyList],
    }),
    deletePolicy: builder.mutation<void, string>({
      query: (id) => ({
        url: makeUrl(DataUrlConstants.ABAC_POLICY_ITEM, { id }),
        method: 'DELETE',
      }),
      invalidatesTags: [tags.AbacPolicyList],
    }),

    // Resource endpoints
    listResource: builder.query<AbacResource[], string>({
      query: (type) => ({ url: makeUrl(DataUrlConstants.ABAC_RESOURCE, { type }) }),
    }),
    listResourceForValues: builder.query<AbacResource[], string>({
      query: (type) => ({ url: makeUrl(DataUrlConstants.ABAC_RESOURCE_VALUES, { type }) }),
    }),
    getAttributesOfResource: builder.query<AbacAttributeResponseDTO[], { type: string; id: string }>({
      query: ({ type, id }) => ({ url: makeUrl(DataUrlConstants.ABAC_RESOURCE_ATTRIBUTES, { type, id }) }),
    }),
    getAttributesTokens: builder.query<Record<string, KeyValue[]>, { type: string; id: string }>({
      query: ({ type, id }) => ({ url: makeUrl(DataUrlConstants.ABAC_RESOURCE_TOKENS, { type, id }) }),
    }),
    listResourceType: builder.query<ResourceType[], void>({
      query: () => ({ url: DataUrlConstants.ABAC_RESOURCE_TYPE }),
    }),
  }),
});

export const {
  useListAttributesQuery,
  useGetAttributeQuery,
  useAddAttributeMutation,
  useEditAttributesMutation,
  useDeleteAttributeMutation,
  useGetUserAttributeQuery,
  useGetSupportedDataTypesQuery,
  useListAttributeValuesQuery,
  useGetAttributeValueQuery,
  useAddAttributeValueMutation,
  useDeleteAttributeValueMutation,
  useDeleteAttributeValuesMutation,
  useListPoliciesQuery,
  useGetPolicyQuery,
  useAddPolicyMutation,
  useEditPolicyMutation,
  useDeletePolicyMutation,
  useListResourceQuery,
  useListResourceForValuesQuery,
  useGetAttributesOfResourceQuery,
  useGetAttributesTokensQuery,
  useListResourceTypeQuery,
} = abacApi;
