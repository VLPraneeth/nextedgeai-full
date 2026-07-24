//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { injectEndpoints, tags } from '../api';
import {
  ErrorNotificationCadence,
  ErrorNotificationConfig,
  ErrorNotificationInvitationQuery,
  ErrorNotificationTestBody,
  ErrorNotificationTestResponse,
  ErrorNotificationType,
} from './types';

const customSynapseApi = injectEndpoints({
  endpoints: (builder) => ({
    getErrorNotificationConfigs: builder.query<ErrorNotificationConfig[], void>({
      query: () => {
        return {
          url: DataUrlConstants.ERROR_NOTIFICATION_CONFIGURATIONS,
          method: 'GET',
        };
      },
      providesTags: () => [tags.ErrorNotificationList],
    }),
    getErrorNotificationTypes: builder.query<ErrorNotificationType[], void>({
      query: () => {
        return {
          url: DataUrlConstants.ERROR_NOTIFICATION_TYPES,
          method: 'GET',
        };
      },
    }),
    getErrorNotificationCadences: builder.query<ErrorNotificationCadence[], void>({
      query: () => {
        return {
          url: DataUrlConstants.ERROR_NOTIFICATION_CADENCES,
          method: 'GET',
        };
      },
    }),
    getErrorNotificationConfigItem: builder.query<ErrorNotificationConfig, string | undefined>({
      query: (id) => {
        return {
          url: makeUrl(DataUrlConstants.ERROR_NOTIFICATION_CONFIGURATION_ITEM, { id }),
          method: 'GET',
        };
      },
      providesTags: (result) => [...(result?.id ? [tags.ErrorNotification(result?.id)] : [])],
    }),
    getErrorNotificationWebhookBody: builder.query<JSON, void>({
      query: () => {
        return {
          url: makeUrl(DataUrlConstants.ERROR_NOTIFICATION_WEBHOOK_BODY),
          method: 'GET',
        };
      },
    }),
    createErrorNotificationsConfig: builder.mutation<ErrorNotificationConfig, ErrorNotificationConfig>({
      query: (params) => {
        return {
          url: DataUrlConstants.ERROR_NOTIFICATION_CONFIGURATIONS,
          method: 'POST',
          body: params,
        };
      },
      invalidatesTags: () => [tags.ErrorNotificationList],
    }),
    postErrorNotificationsTest: builder.mutation<ErrorNotificationTestResponse, ErrorNotificationTestBody>({
      query: (params) => {
        return {
          url: DataUrlConstants.ERROR_NOTIFICATION_CONFIGURATION_TEST,
          method: 'POST',
          body: params,
        };
      },
    }),
    errorNotificationInviteUser: builder.query<string, ErrorNotificationInvitationQuery>({
      query: ({ encInstanceId, invitationId, status }) => {
        return {
          url: makeUrl(DataUrlConstants.ERROR_NOTIFICATION_INVITE_USER, { encInstanceId, invitationId, status }),
          method: 'GET',
        };
      },
    }),
    updateErrorNotificationsConfig: builder.mutation<ErrorNotificationConfig, ErrorNotificationConfig>({
      query: (params) => {
        return {
          url: makeUrl(DataUrlConstants.ERROR_NOTIFICATION_CONFIGURATION_ITEM, { id: params.id }),
          method: 'PUT',
          body: params,
        };
      },
      invalidatesTags: (result) => [
        tags.ErrorNotificationList,
        ...(result?.id ? [tags.ErrorNotification(result?.id)] : []),
      ],
    }),
    deleteErrorNotificationsConfig: builder.mutation<ErrorNotificationConfig, string | undefined>({
      query: (id) => {
        return {
          url: makeUrl(DataUrlConstants.ERROR_NOTIFICATION_CONFIGURATION_ITEM, { id }),
          method: 'DELETE',
        };
      },
      invalidatesTags: () => [tags.ErrorNotificationList],
    }),
    resendInvite: builder.mutation<any, { email: string; id: string }>({
      query: (params) => ({
        url: makeUrl(DataUrlConstants.ERROR_NOTIFICATION_RESEND_INVITE, params),
        method: 'POST',
      }),
      invalidatesTags: [tags.ErrorNotificationList],
    }),
  }),
});

export const {
  useGetErrorNotificationCadencesQuery,
  useGetErrorNotificationTypesQuery,
  useGetErrorNotificationConfigsQuery,
  useCreateErrorNotificationsConfigMutation,
  useGetErrorNotificationConfigItemQuery,
  useUpdateErrorNotificationsConfigMutation,
  useDeleteErrorNotificationsConfigMutation,
  usePostErrorNotificationsTestMutation,
  useGetErrorNotificationWebhookBodyQuery,
  useErrorNotificationInviteUserQuery,
  useResendInviteMutation,
} = customSynapseApi;
