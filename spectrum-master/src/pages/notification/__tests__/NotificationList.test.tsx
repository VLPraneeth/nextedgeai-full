// @ts-nocheck
import * as NotificationsApi from 'store/notifications/api';
import { Notification, NotificationTypes } from 'store/notifications/types';
import { fireEvent, render, screen } from 'tests/helpers';

import NotificationList from '../NotificationList';

jest.mock('utils/AjaxUtil');

const sampleErrorNotification: Notification = {
  archived: false,
  body: '',
  createdAt: '2020-06-18T01:47:39.570+0000',
  createdBy: '5e0d966414',
  id: '111',
  read: false,
  subject: 'Pipeline Error',
  type: NotificationTypes.ERROR,
  updatedAt: '2020-06-18T01:47:39.570+0000',
  updatedBy: '5e0d966414',
  userId: '5e0d46172a',
};

const sampleWarningNotification: Notification = {
  archived: false,
  body: 'There was an issue with Pipeline 2',
  createdAt: '2020-06-18T01:47:39.570+0000',
  createdBy: '5e0d966414',
  id: '222',
  read: false,
  subject: 'Pipeline Warning',
  type: NotificationTypes.WARN,
  updatedAt: '2020-06-18T01:47:39.570+0000',
  updatedBy: '5e0d966414',
  userId: '5e0d46172a',
};

test('renders empty', async () => {
  render(<NotificationList />);
  expect(await screen.findByText('Filter')).toBeInTheDocument();
});

test('renders single notification', async () => {
  jest.spyOn(NotificationsApi, 'useGetNotificationsQuery').mockImplementation(() => ({
    data: [sampleErrorNotification],
    isLoading: false,
  }));

  render(<NotificationList />);

  expect(await screen.findByText('Pipeline Error')).toBeInTheDocument();
});

test('renders multiple notifications, can expand, marks expanded notification as read', async () => {
  jest.spyOn(NotificationsApi, 'useGetNotificationsQuery').mockImplementation(() => ({
    data: [sampleErrorNotification, sampleWarningNotification],
    isLoading: false,
  }));

  const markAsReadMutation = jest.fn(() => Promise.resolve({ data: undefined }));
  jest.spyOn(NotificationsApi, 'useMarkNotificationsReadMutation').mockReturnValue([markAsReadMutation, {}]);

  render(<NotificationList />);

  expect(await screen.findByText('Pipeline Error')).toBeInTheDocument();
  expect(await screen.findByText('Pipeline Warning')).toBeInTheDocument();

  fireEvent.click(await screen.findByLabelText('Expand details for notification id: 222'));

  expect(await screen.findByText('There was an issue with Pipeline 2')).toBeInTheDocument();

  expect(markAsReadMutation).toHaveBeenCalledWith([sampleWarningNotification.id]);
});

test("Archive all notifications, with notifications expanded, doesn't crash.", async () => {
  jest.spyOn(NotificationsApi, 'useGetNotificationsQuery').mockImplementation(() => ({
    data: [sampleErrorNotification, sampleWarningNotification],
    isLoading: false,
  }));

  const archiveAllMutation = jest.fn(() => Promise.resolve({ data: undefined }));
  jest.spyOn(NotificationsApi, 'useArchiveAllNotificationsMutation').mockReturnValue([archiveAllMutation, {}]);

  render(<NotificationList />);

  fireEvent.click(await screen.findByLabelText('Archive all'));

  expect(archiveAllMutation).toHaveBeenCalled();
});
