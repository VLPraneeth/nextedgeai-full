import { createHistory, createMemorySource, LocationProvider, Router } from '@reach/router';

import { NavigateConfirmationModal } from 'components/NavigateConfirmationModal';
import { mockedAjaxUtils, render, screen, userEvent, waitFor } from 'tests/helpers';
import { AllPermissions } from 'utils/PermissionsConstants';

import ErrorNotifications from './ErrorNotifications';
import {
  mockCadences,
  mockNotificationTypes,
  mockWebhookBody,
  mockWebhookNotificationCreate,
  mockWebhookNotificationUpdate,
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
      data: [mockWebhookNotificationCreate],
      isLoading: false,
    }),
    useDeleteErrorNotificationsConfigMutation: () => [jest.fn()],
    useGetErrorNotificationTypesQuery: () => ({ data: mockNotificationTypes, isLoading: false }),
    useGetErrorNotificationCadencesQuery: () => ({ data: mockCadences, isLoading: false }),
    useGetErrorNotificationConfigItemQuery: () => ({
      data: mockWebhookNotificationCreate,
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

describe('Webhook notifications', async () => {
  afterEach(() => jest.clearAllMocks());

  it('Creates webhook notification', async () => {
    renderComp('webhook', '/add');

    expect(screen.getByText('Create webhook notification')).toBeVisible();
    expect(screen.getByText('Save').closest('button')).toBeDisabled();

    await userEvent.type(screen.getByLabelText('Name*'), mockWebhookNotificationCreate.name);
    await userEvent.type(screen.getByLabelText('Description'), mockWebhookNotificationCreate.description);

    await userEvent.click(screen.getByTestId('notificationType'));
    mockNotificationTypes.forEach((type) => {
      expect(screen.getByText(type.title)).toBeVisible();
    });
    await userEvent.click(screen.getByText(mockNotificationTypes[0].title));
    await userEvent.click(screen.getByText(mockNotificationTypes[1].title));

    const endpointContainer = screen.getByTestId('endpoint');
    await userEvent.type(
      endpointContainer.querySelector('.synri-text-input-container input') as HTMLInputElement,
      mockWebhookNotificationCreate.configuration.url
    );

    await waitFor(() => expect(screen.getByText('Save').closest('button')).not.toBeDisabled());
    await userEvent.click(screen.getByText('Save'));

    await waitFor(() => expect(mockSave).toHaveBeenCalledWith(mockWebhookNotificationCreate));
  });

  it('Updates webhook notification', async () => {
    renderComp('webhook', `/${mockWebhookNotificationUpdate.id}/edit`);

    expect(screen.getByText('Edit webhook notification')).toBeVisible();
    expect(screen.getByText('Save').closest('button')).toBeDisabled();

    expect(screen.getByLabelText('Name*')).toHaveValue(mockWebhookNotificationCreate.name);
    expect(screen.getByLabelText('Description')).toHaveValue(mockWebhookNotificationCreate.description);

    const endpointInput = screen
      .getByTestId('endpoint')
      .querySelector('.synri-text-input-container input') as HTMLInputElement;
    expect(endpointInput).toHaveValue(mockWebhookNotificationCreate.configuration.url);

    mockWebhookNotificationCreate.notificationTypes.forEach((typeId) => {
      const notificationTitle = mockNotificationTypes.find((type) => type.id === typeId)?.title;
      notificationTitle && expect(screen.getByText(notificationTitle)).toBeVisible();
    });

    await userEvent.clear(screen.getByLabelText('Name*'));
    await userEvent.type(screen.getByLabelText('Name*'), mockWebhookNotificationUpdate.name);
    await userEvent.clear(screen.getByLabelText('Description'));
    await userEvent.type(screen.getByLabelText('Description'), mockWebhookNotificationUpdate.description);

    expect(screen.getByRole('switch')).toHaveAttribute('aria-checked', 'true');
    await userEvent.click(screen.getByRole('switch'));
    expect(screen.getByRole('switch')).toHaveAttribute('aria-checked', 'false');

    expect(screen.getByRole('radio', { name: 'Real time' })).toBeChecked();
    await userEvent.click(screen.getByRole('radio', { name: 'Hourly' }));
    expect(screen.getByRole('radio', { name: 'Real time' })).not.toBeChecked();
    expect(screen.getByRole('radio', { name: 'Hourly' })).toBeChecked();

    expect(screen.getByText('Header/Body')).toBeVisible();
    expect(screen.getByText('Test Webhook')).toBeVisible();

    await userEvent.click(screen.getByText('Save'));

    await waitFor(() => expect(mockSave).toHaveBeenCalledWith(mockWebhookNotificationUpdate));
  });
});
