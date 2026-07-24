import { createHistory, createMemorySource, LocationProvider, Router } from '@reach/router';

import { NavigateConfirmationModal } from 'components/NavigateConfirmationModal';
import { mockedAjaxUtils, render, screen, userEvent, waitFor } from 'tests/helpers';
import { AllPermissions } from 'utils/PermissionsConstants';

import ErrorNotifications from './ErrorNotifications';
import {
  mockCadences,
  mockEmailNotificationCreate,
  mockEmailNotificationUpdate,
  mockNotificationTypes,
  mockWebhookBody,
} from './test-utils';
import { NotificationTypes } from './utils';

jest.mock('utils/AjaxUtil');
const ajaxMock = mockedAjaxUtils();
ajaxMock.post.mockImplementation(() => Promise.resolve({ success: true }));
ajaxMock.put.mockImplementation(() => Promise.resolve({ success: true }));

const mockSave = jest.fn().mockReturnValue({ unwrap: () => Promise.resolve('success') });

jest.mock('store/error-notifications-v2/api', () => {
  return {
    useGetErrorNotificationConfigsQuery: () => ({
      data: [mockEmailNotificationCreate],
      isLoading: false,
    }),
    useDeleteErrorNotificationsConfigMutation: () => [jest.fn()],
    useGetErrorNotificationTypesQuery: () => ({ data: mockNotificationTypes, isLoading: false }),
    useGetErrorNotificationCadencesQuery: () => ({ data: mockCadences, isLoading: false }),
    useGetErrorNotificationConfigItemQuery: () => ({
      data: mockEmailNotificationCreate,
      isLoading: false,
    }),
    useCreateErrorNotificationsConfigMutation: () => [mockSave, { isLoading: false }],
    useUpdateErrorNotificationsConfigMutation: () => [mockSave, { isLoading: false }],
    usePostErrorNotificationsTestMutation: () => [jest.fn(), { isLoading: false }],
    useGetErrorNotificationWebhookBodyQuery: () => ({ data: mockWebhookBody, isLoading: false }),
  };
});

const renderComp = (notificationType: NotificationTypes, operation: string = '') => {
  return render(
    <NavigateConfirmationModal>
      <LocationProvider
        history={createHistory(createMemorySource(`/settings/notifications/${notificationType}${operation}`))}>
        <Router>
          <ErrorNotifications path="/settings/notifications/*" />
        </Router>
      </LocationProvider>
    </NavigateConfirmationModal>,
    {
      testState: {
        user: {
          privileges: [
            AllPermissions.READ_ERROR_NOTIFICATION_EMAIL,
            AllPermissions.READ_ERROR_NOTIFICATION_WEBHOOK,
            AllPermissions.WRITE_ERROR_NOTIFICATION_EMAIL,
            AllPermissions.WRITE_ERROR_NOTIFICATION_WEBHOOK,
          ],
        },
      },
    }
  );
};

describe('Email notifications', async () => {
  afterEach(() => jest.clearAllMocks());

  it('Creates email notification', async () => {
    renderComp('email', '/add');

    expect(screen.getByText('Create email notification')).toBeVisible();
    expect(screen.getByText('Save').closest('button')).toBeDisabled();

    await userEvent.type(screen.getByLabelText('Name*'), mockEmailNotificationCreate.name);
    await userEvent.type(screen.getByLabelText('Description'), mockEmailNotificationCreate.description);

    await userEvent.click(screen.getByTestId('notificationType'));
    mockNotificationTypes.forEach((type) => {
      expect(screen.getByText(type.title)).toBeVisible();
    });
    await userEvent.click(screen.getByText(mockNotificationTypes[0].title));
    await userEvent.click(screen.getByText(mockNotificationTypes[1].title));

    const emailInput = screen.getByTestId('emailInput').querySelector('input') as HTMLInputElement;

    await userEvent.type(emailInput, mockEmailNotificationCreate.configuration.emails[0].email);
    // Ant design added "icon: check" to the name
    await userEvent.click(
      screen.getByRole('option', { name: mockEmailNotificationCreate.configuration.emails[0].email + ' icon: check' })
    );

    expect(screen.getByText('Save').closest('button')).not.toBeDisabled();
    await userEvent.click(screen.getByText('Save'));

    await waitFor(() => expect(mockSave).toHaveBeenCalledWith(mockEmailNotificationCreate));
  });

  it('Updates email notification', async () => {
    renderComp('email', `/${mockEmailNotificationUpdate.id}/edit`);

    expect(screen.getByText('Edit email notification')).toBeVisible();

    expect(screen.getByLabelText('Name*')).toHaveValue(mockEmailNotificationCreate.name);
    expect(screen.getByLabelText('Description')).toHaveValue(mockEmailNotificationCreate.description);

    expect(screen.getByText(mockEmailNotificationCreate.configuration.emails[0].email)).toBeVisible();

    mockEmailNotificationCreate.notificationTypes.forEach((typeId) => {
      const notificationTitle = mockNotificationTypes.find((type) => type.id === typeId)?.title;
      notificationTitle && expect(screen.getByText(notificationTitle)).toBeVisible();
    });

    await userEvent.clear(screen.getByLabelText('Name*'));
    await userEvent.type(screen.getByLabelText('Name*'), mockEmailNotificationUpdate.name);
    await userEvent.clear(screen.getByLabelText('Description'));
    await userEvent.type(screen.getByLabelText('Description'), mockEmailNotificationUpdate.description);

    expect(screen.getByRole('switch')).toHaveAttribute('aria-checked', 'true');
    await userEvent.click(screen.getByRole('switch'));
    expect(screen.getByRole('switch')).toHaveAttribute('aria-checked', 'false');

    expect(screen.getByRole('radio', { name: 'Real time' })).toBeChecked();
    await userEvent.click(screen.getByRole('radio', { name: 'Hourly' }));
    expect(screen.getByRole('radio', { name: 'Real time' })).not.toBeChecked();
    expect(screen.getByRole('radio', { name: 'Hourly' })).toBeChecked();

    await userEvent.click(screen.getByText('Save'));

    await waitFor(() => expect(mockSave).toHaveBeenCalledWith(mockEmailNotificationUpdate));
  });
});
