import DataUrlConstants from 'utils/DataUrlConstants';
import { makeUrl } from 'utils/UrlUtil';

import { injectEndpoints, tags } from '../api';
import { Notification, NotificationTypes } from './types';

interface GetNotificationParams {
  type?: NotificationTypes;
  isArchived?: boolean;
  isRead?: boolean;
}

const defaultParams: GetNotificationParams = { type: NotificationTypes.ALL, isArchived: false };

const notificationsApi = injectEndpoints({
  endpoints: (builder) => ({
    getNotifications: builder.query<Notification[], GetNotificationParams>({
      query: (params) => {
        return { url: makeUrl(DataUrlConstants.GET_NOTIFICATIONS, {}, { ...defaultParams, ...params }) };
      },
      providesTags: [tags.NotificationsList],
    }),
    getUnreadNotifications: builder.query<Notification[], void>({
      query: () => {
        return {
          url: makeUrl(DataUrlConstants.GET_NOTIFICATIONS, {
            ...defaultParams,
            isRead: false,
          }),
        };
      },
      providesTags: [tags.NotificationsList],
    }),
    getUnreadNotificationCount: builder.query<number, void>({
      query: () => {
        return { url: DataUrlConstants.GET_NOTIFICATIONS_UNREAD_COUNT };
      },
      providesTags: [tags.NotificationsList],
    }),
    markNotificationsRead: builder.mutation<void, string[]>({
      query: (notificationIds) => {
        return {
          method: 'PUT',
          url: DataUrlConstants.MARK_NOTIFICATIONS_AS_READ,
          body: notificationIds,
        };
      },
      invalidatesTags: [tags.NotificationsList],
    }),
    markNotificationsUnread: builder.mutation<void, string[]>({
      query: (notificationIds) => {
        return {
          method: 'PUT',
          url: DataUrlConstants.MARK_NOTIFICATIONS_AS_UNREAD,
          body: notificationIds,
        };
      },
      invalidatesTags: [tags.NotificationsList],
    }),
    archiveNotifications: builder.mutation<void, string[]>({
      query: (notificationIds) => {
        return {
          method: 'PUT',
          url: DataUrlConstants.ARCHIVE_NOTIFICATIONS,
          body: notificationIds,
        };
      },
      invalidatesTags: [tags.NotificationsList],
    }),
    archiveAllNotifications: builder.mutation<void, void>({
      query: () => {
        return {
          method: 'PUT',
          url: DataUrlConstants.ARCHIVE_ALL_NOTIFICATIONS,
        };
      },
      invalidatesTags: [tags.NotificationsList],
    }),
    markAllNotificationRead: builder.mutation<void, void>({
      query: () => {
        return {
          method: 'PUT',
          url: DataUrlConstants.MARK_ALL_NOTIFICATIONS_AS_READ,
        };
      },
      invalidatesTags: [tags.NotificationsList],
    }),
  }),
});

export const {
  endpoints: notificationEndpoints,
  useArchiveAllNotificationsMutation,
  useArchiveNotificationsMutation,
  useGetNotificationsQuery,
  useGetUnreadNotificationCountQuery,
  useGetUnreadNotificationsQuery,
  useLazyGetUnreadNotificationsQuery,
  useLazyGetUnreadNotificationCountQuery,
  useMarkAllNotificationReadMutation,
  useMarkNotificationsReadMutation,
  useMarkNotificationsUnreadMutation,
} = notificationsApi;
