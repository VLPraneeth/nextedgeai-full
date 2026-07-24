//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { injectEndpoints, tags } from '../api';
import {
  AllSharedDashboard,
  AllowedDomains,
  InsightsDashboard,
  ReshareResponse,
  SharingDashboardPayload,
  SharingDashboardResponse,
  SharingDetailsPayload,
  SharingDetailsResponse,
  UpdateExpiryPayload,
} from './types';

const sharingApi = injectEndpoints({
  endpoints: (builder) => ({
    createShareDashboardInvite: builder.mutation<SharingDashboardResponse[], SharingDashboardPayload>({
      query: (payload) => ({
        url: makeUrl(DataUrlConstants.INSIGHTS_SHARING),
        method: 'POST',
        body: payload,
      }),
      invalidatesTags: [tags.InsightsSharingDetailsList],
    }),
    createAllowedDomains: builder.mutation<AllowedDomains, AllowedDomains>({
      query: (payload) => ({
        url: makeUrl(DataUrlConstants.ALLOWED_DOMAINS),
        method: 'POST',
        body: payload,
      }),
      invalidatesTags: [tags.InsightsSharingAllowedDomainsList],
    }),
    getAllowedDomains: builder.query<AllowedDomains, void>({
      query: () => ({
        url: makeUrl(DataUrlConstants.LIST_DOMAINS),
      }),
      providesTags: [tags.InsightsSharingAllowedDomainsList],
    }),
    fetchDomainsInUse: builder.mutation<string[], AllowedDomains>({
      query: (body) => ({
        url: makeUrl(DataUrlConstants.EMAIL_DOMAINS_IN_USE),
        method: 'POST',
        body,
      }),
    }),
    getSharingDetails: builder.query<SharingDetailsResponse, SharingDetailsPayload>({
      query: ({ cursor, dashboardId, direction, pageSize, predicate }) => ({
        url: makeUrl(
          DataUrlConstants.INSIGHTS_SHARING_DETAILS_WITH_ID,
          { dashboardId },
          {
            cursor,
            pageSize,
            direction,
          }
        ),
        method: 'POST',
        body: predicate,
      }),
      providesTags: [tags.InsightsSharingDetailsList],
    }),
    updateExpiry: builder.mutation<boolean, UpdateExpiryPayload>({
      query: ({ expiryDate, sharedItemId }) => ({
        url: makeUrl(
          DataUrlConstants.INSIGHTS_SHARING_DETAILS,
          {},
          {
            expiryDate,
            sharedItemId,
          }
        ),
        method: 'PUT',
      }),
      invalidatesTags: [tags.InsightsSharingDetailsList],
    }),
    deleteSharingDetails: builder.mutation<boolean, string[]>({
      query: (sharedItemIds) => ({
        url: makeUrl(DataUrlConstants.INSIGHTS_SHARING_DETAILS, {}, { sharedItemIds }),
        method: 'DELETE',
      }),
      invalidatesTags: [tags.InsightsSharingDetailsList],
    }),
    reshare: builder.mutation<ReshareResponse[], string[]>({
      query: (sharedItemIds) => ({
        url: makeUrl(DataUrlConstants.INSIGHTS_SHARING_RESHARE),
        body: sharedItemIds,
        method: 'POST',
      }),
      invalidatesTags: [tags.InsightsSharingDetailsList],
    }),
    getAllSharedDashboards: builder.query<AllSharedDashboard[], void>({
      query: () => ({
        url: makeUrl(DataUrlConstants.INSIGHTS_SHARING_ALL_SHARED_DASHBOARDS),
      }),
    }),
    getSharedDashboard: builder.query<InsightsDashboard, string>({
      query: (dashboardId) => ({
        url: makeUrl(DataUrlConstants.INSIGHTS_SHARING_DASHBOARD_WITH_ID, { dashboardId }),
      }),
      keepUnusedDataFor: 0,
    }),
  }),
});

export const {
  useCreateShareDashboardInviteMutation,
  useCreateAllowedDomainsMutation,
  useGetAllowedDomainsQuery,
  useGetSharingDetailsQuery,
  useLazyGetSharingDetailsQuery,
  useUpdateExpiryMutation,
  useDeleteSharingDetailsMutation,
  useReshareMutation,
  useGetAllSharedDashboardsQuery,
  useGetSharedDashboardQuery,
  useFetchDomainsInUseMutation,
} = sharingApi;
