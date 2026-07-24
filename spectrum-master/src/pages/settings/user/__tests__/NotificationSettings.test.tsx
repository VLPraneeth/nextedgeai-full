import configureStore from 'store/configureStore';
import { render, screen, userEvent } from 'tests/helpers';

import NotificationSettings from '../NotificationSettings';

const store = configureStore({
  user: {
    errorMessage: '',
    errorMessages: [],
    userPref: {
      errorNotification: {
        subscriptions: [
          {
            catalogId: '62829952b1e81b907e8fc68a',
            active: true,
            frequency: 'HOURLY',
            channels: ['email'],
          },
          {
            catalogId: '62829952b1e81b907e8fc68c',
            active: true,
            frequency: 'DAILY',
            channels: ['email'],
          },
          {
            catalogId: '62829952b1e81b907e8fc68b',
            active: false,
            frequency: 'HOURLY',
            channels: ['email'],
          },
        ],
        channelConfigurations: [
          {
            type: 'email',
            active: true,
            configuration: {
              emails: ['dan@syncari.com', 'francis@syncari.com'],
            },
          },
        ],
      },
    },
  },
});

describe('NotificationSettings', () => {
  test('should render existing settings', async () => {
    render(<NotificationSettings />);

    expect(await screen.findByTestId('loading-notification-settings')).toBeVisible();
  });

  test('should show options from the error catalog metadata', async () => {
    render(<NotificationSettings />, { store });

    expect(await screen.findByText('Notifications')).toBeVisible();
  });

  test('should show previously selected options in user preferences', async () => {
    render(<NotificationSettings />, { store });

    const syncErrorsSwitch = await screen.findByLabelText('Sync Errors');

    // The Sync Errors is checked in the settings so it should be visible and checked
    expect(syncErrorsSwitch).toBeVisible();
    expect(syncErrorsSwitch).toHaveAttribute('aria-checked', 'true');

    await userEvent.click(syncErrorsSwitch);
    expect(syncErrorsSwitch).toHaveAttribute('aria-checked', 'false');

    // The Synapse Errors is not checked in the settings so it should be visible
    // and unchecked
    const synapseErrorsSwitch = await screen.findByLabelText('Synapse Errors');

    expect(synapseErrorsSwitch).toBeVisible();
    expect(synapseErrorsSwitch).toHaveAttribute('aria-checked', 'false');
  });
});
