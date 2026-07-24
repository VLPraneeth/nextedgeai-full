//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { CustomSynapse } from 'components/custom-synapse/types';
import { SupportedAuthType } from 'store/credential/types';
import { CustomActionTestingResponse } from 'store/custom-action/types';
import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { injectEndpoints, tags } from '../../api';
import {
  CustomSynapseShare,
  CustomSynapseShareScope,
  EntityPaginationItem,
  HTTPCustomSynapseEntity,
  HTTPCustomSynapseEntityMeta,
  HTTPCustomSynapseEntityTestingPayload,
} from '../types';

const httpCustomSynapseApi = injectEndpoints({
  endpoints: (builder) => ({
    getHttpCustomSypapseDatatypes: builder.query<{ value: string; label: string }[] | undefined, void>({
      query: () => ({ url: DataUrlConstants.HTTP_CUSTOM_SYNAPSE_DATATYPES }),
    }),
    getHttpCustomSypapseAuthtypes: builder.query<SupportedAuthType[] | undefined, void>({
      query: () => ({ url: DataUrlConstants.HTTP_CUSTOM_SYNAPSE_AUTHTYPES }),
    }),
    httpCustomSypapseTest: builder.mutation<
      CustomActionTestingResponse,
      Partial<HTTPCustomSynapseEntityTestingPayload>
    >({
      query: (params) => {
        return {
          url: DataUrlConstants.HTTP_CUSTOM_SYNAPSE_TEST,
          method: 'POST',
          body: params,
        };
      },
    }),
    createDraftHttpCustomSynapse: builder.mutation<CustomSynapse, string>({
      query: (id) => {
        return {
          url: makeUrl(DataUrlConstants.HTTP_CUSTOM_SYNAPSE_CREATE_DRAFT, { metadataId: id }),
          method: 'POST',
        };
      },
      invalidatesTags: [tags.AllCustomSynapseList],
    }),
    discardDraftHttpCustomSynapse: builder.mutation<null, string>({
      query: (id) => {
        return {
          url: makeUrl(DataUrlConstants.HTTP_CUSTOM_SYNAPSE_DISCARD_DRAFT, { metadataId: id }),
          method: 'POST',
        };
      },
      invalidatesTags: [tags.AllCustomSynapseList],
    }),
    publishHttpCustomSynapse: builder.mutation<CustomSynapse, string>({
      query: (id) => {
        return {
          url: makeUrl(DataUrlConstants.HTTP_CUSTOM_SYNAPSE_PUBLISH, { metadataId: id }),
          method: 'POST',
        };
      },
      invalidatesTags: [tags.AllCustomSynapseList],
    }),
    getHttpCustomSynapseEntityList: builder.query<HTTPCustomSynapseEntityMeta[], string>({
      query: (id) => ({ url: makeUrl(DataUrlConstants.HTTP_CUSTOM_SYNAPSE_ENTITY, { metadataId: id }) }),
      providesTags: (result) => [
        ...(result || []).map((entity: HTTPCustomSynapseEntityMeta) => tags.CustomSynapseEntity(entity.id)),
        tags.CustomSynapseEntityList,
      ],
    }),
    createHttpCustomSynapseEntity: builder.mutation<
      HTTPCustomSynapseEntity,
      { body: HTTPCustomSynapseEntity; metadataId: string }
    >({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.HTTP_CUSTOM_SYNAPSE_ENTITY, { metadataId: params.metadataId }),
          method: 'POST',
          body: params.body,
        };
      },
      invalidatesTags: [tags.CustomSynapseEntityList],
    }),
    getHttpCustomSynapseEntityItem: builder.query<HTTPCustomSynapseEntity, { metadataId: string; entityId: string }>({
      query: (params) => ({ url: makeUrl(DataUrlConstants.HTTP_CUSTOM_SYNAPSE_ENTITY_ITEM, params) }),
      providesTags: (result) => [...(result?.id ? [tags.CustomSynapseEntity(result?.id)] : [])],
    }),
    updateHttpCustomSynapseEntityItem: builder.mutation<
      HTTPCustomSynapseEntity,
      { body: HTTPCustomSynapseEntity; entityId: string; metadataId: string }
    >({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.HTTP_CUSTOM_SYNAPSE_ENTITY_ITEM, {
            entityId: params.entityId,
            metadataId: params.metadataId,
          }),
          method: 'PUT',
          body: params.body,
        };
      },
      invalidatesTags: (result) => [tags.CustomSynapseEntityList, tags.CustomSynapseEntity(result?.id!)],
    }),
    deleteCustomSynapseEntity: builder.mutation<HTTPCustomSynapseEntity, { metadataId: string; entityId: string }>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.HTTP_CUSTOM_SYNAPSE_ENTITY_ITEM, params),
          method: 'DELETE',
        };
      },
      invalidatesTags: [tags.CustomSynapseEntityList],
    }),
    generateHttpCustomSynapseEntitySchema: builder.mutation<Record<string, any>, Record<string, any>>({
      query: (body) => {
        return {
          url: makeUrl(DataUrlConstants.HTTP_CUSTOM_SYNAPSE_ENTITY_SCHEMA),
          method: 'POST',
          body,
        };
      },
    }),
    getHttpCustomSynapseEntityPagination: builder.query<EntityPaginationItem[], void>({
      query: () => ({ url: makeUrl(DataUrlConstants.HTTP_CUSTOM_SYNAPSE_ENTITY_PAGINATION) }),
    }),
    getCustomSynapseSharingScope: builder.query<CustomSynapseShareScope[], void>({
      query: () => ({ url: makeUrl(DataUrlConstants.CUSTOM_SYNAPSE_SHARE_SCOPE) }),
    }),
    getCustomSynapseShareStatus: builder.query<CustomSynapseShare, string>({
      query: (id) => ({ url: makeUrl(DataUrlConstants.CUSTOM_SYNAPSE_SHARE, { connectorMetaDefinitionId: id }) }),
      providesTags: (result, _, id) => [tags.CustomSynapseSharing(id)],
    }),
    shareCustomSynapse: builder.mutation<CustomSynapseShare, { shareOptions: CustomSynapseShare; id: string }>({
      query: (body) => {
        return {
          url: makeUrl(DataUrlConstants.CUSTOM_SYNAPSE_SHARE, {
            connectorMetaDefinitionId: body.id,
          }),
          method: 'POST',
          body: body.shareOptions,
        };
      },
      invalidatesTags: (result, _, body) => [tags.AllCustomSynapseList, tags.CustomSynapseSharing(body.id)],
    }),
  }),
});

export const {
  useGetHttpCustomSypapseDatatypesQuery,
  useGetHttpCustomSypapseAuthtypesQuery,
  useHttpCustomSypapseTestMutation,
  usePublishHttpCustomSynapseMutation,
  useCreateDraftHttpCustomSynapseMutation,
  useDiscardDraftHttpCustomSynapseMutation,
  useGetHttpCustomSynapseEntityListQuery,
  useGetHttpCustomSynapseEntityItemQuery,
  useCreateHttpCustomSynapseEntityMutation,
  useUpdateHttpCustomSynapseEntityItemMutation,
  useGenerateHttpCustomSynapseEntitySchemaMutation,
  useGetHttpCustomSynapseEntityPaginationQuery,
  useDeleteCustomSynapseEntityMutation,
  useGetCustomSynapseSharingScopeQuery,
  useGetCustomSynapseShareStatusQuery,
  useShareCustomSynapseMutation,
} = httpCustomSynapseApi;
