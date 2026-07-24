//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import DataUrlConstants from 'utils/DataUrlConstants';
import { EntityTag } from 'store/api/types';

import { injectEndpoints } from '../api';
import { AllRolesAllInstance, CustomPreference, GhostAccessAudit, RequestGhost, User } from './types';

type GetUserRolesByIdParam = {
  userId: string;
};

type GetGhostAccessParams = {
  status?: string;
};

const userApi = injectEndpoints({
  endpoints: (builder) => ({
    getGhostAccess: builder.query<GhostAccessAudit[], GetGhostAccessParams | undefined | void>({
      query: (params) => ({
        url: DataUrlConstants.GET_GHOST_ACCESS,
        params: params || {},
      }),
      providesTags: [EntityTag.GHOST_ACCESS],
    }),
    requestGhostAccess: builder.mutation<RequestGhost, RequestGhost>({
      query: (params) => ({
        url: DataUrlConstants.REQUEST_GHOST_ACCESS,
        method: 'POST',
        body: params,
      }),
      invalidatesTags: [EntityTag.GHOST_ACCESS],
    }),
    revokeGhostAccess: builder.mutation<RequestGhost, Partial<RequestGhost>>({
      query: (params) => ({
        url: DataUrlConstants.REVOKE_GHOST_ACCESS,
        method: 'POST',
        body: params,
      }),
      invalidatesTags: [EntityTag.GHOST_ACCESS],
    }),
    getSyncariDevUsers: builder.query<User[], void>({
      query: () => ({
        url: DataUrlConstants.GET_SYNCARI_DEV_USERS,
      }),
    }),
    getAllRolesAllInstances: builder.query<AllRolesAllInstance, undefined | void>({
      query: (params) => ({
        url: DataUrlConstants.GET_ALL_ROLES_ALL_INSTANCES,
        method: 'GET',
        body: params,
      }),
    }),
    getUserRolesById: builder.query<AllRolesAllInstance, GetUserRolesByIdParam>({
      query: (params) => ({
        url: DataUrlConstants.GET_ALL_ROLES_ALL_INSTANCES,
        method: 'POST',
        params,
      }),
    }),
    setCustomPreference: builder.mutation<CustomPreference, CustomPreference>({
      query: (params) => ({
        url: DataUrlConstants.CUSTOM_PREFERENCE,
        method: 'POST',
        body: params,
      }),
    }),
    getCustomPreference: builder.query<CustomPreference, void>({
      query: () => ({
        url: DataUrlConstants.CUSTOM_PREFERENCE,
      }),
    }),
  }),
});

export const {
  useGetGhostAccessQuery,
  useGetAllRolesAllInstancesQuery,
  useRequestGhostAccessMutation,
  useRevokeGhostAccessMutation,
  useGetSyncariDevUsersQuery,
  useGetUserRolesByIdQuery,
  useSetCustomPreferenceMutation,
  useGetCustomPreferenceQuery,
} = userApi;
